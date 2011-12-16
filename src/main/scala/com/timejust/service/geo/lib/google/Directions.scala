package com.timejust.service.geo.lib.google

import akka.actor._
import akka.event.EventHandler
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.service.geo.lib.timejust.Plugin._
import com.timejust.service.geo.lib.timejust.DirectionPlugin._
import com.timejust.service.geo.lib.timejust.AsyncHttpClientPool._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import scala.collection.immutable.HashMap._

/**
 * Implementation of locomote location api in asynchronous manner
 */
object Directions { 
  /**
   *
   */
  val apiUrl = "http://maps.googleapis.com/maps/api/directions/json"         
     
  val pluginName = "google"
  
  /**
   * Create the fetchers
   */
  val plugin = (new Factory()).createPlugin(4, Actor.actorOf[DirectionsActor].start())
     
  class Factory extends PluginFactory {
    override def name() = {
      pluginName
    }
  }
  
  /**
   * Actor to request direction data to locomote api in asynchronous manner
   */ 
  class DirectionsActor extends DirPluggableActor {    
    implicit val formats = DefaultFormats          
    
    override def getModeDriving(): String = { "driving" }
    
    def toSchedule(address: Any, loc:Any) = {
      val map = loc.asInstanceOf[Map[String, Double]];
      Schedule("", address.asInstanceOf[String], map("lat"), map("lng"))
    }
    
    def toDirection(step: Map[String, _]) = {
      val distance = step("distance").asInstanceOf[Map[String, BigInt]]
      val duration = step("duration").asInstanceOf[Map[String, BigInt]]
      
      // Google directions service always return directions for driving.     
      Direction(toSchedule("", step("start_location")), 
        toSchedule("", step("end_location")), "driving", "", "", "", 
        distance("value").intValue(), 
        step("html_instructions").asInstanceOf[String])      
    }
    
    def toSteps(legs: List[Map[String, _]]) = {      
      var steps = List[LocalSteps]() 
      legs.foreach(x=>{
        var directions = List[Direction]()
        x("steps").asInstanceOf[List[Map[String, _]]].foreach(s=>{
          directions ::= toDirection(s)
        })     
        
        val duration = x("duration").asInstanceOf[Map[String, BigInt]]   
        steps ::= new LocalSteps(
          toSchedule(x("start_address"), x("start_location")), 
          toSchedule(x("end_address"), x("end_location")),
          "driving", directions)
      })  
      steps      
    }
    
    def toTrip(route: Map[String, _]) = {
      var departure: Schedule = null
      var arrival: Schedule = null
      val steps = toSteps(route("legs").asInstanceOf[List[Map[String, _]]])
      var duration = 0
      
      steps.foreach(x=>{
        if (duration == 0) {
          departure = x.departure
          duration += 1
        }        
        // Accumulate the duration of direction
        arrival = x.arrival;        
      })
      
      new LocalTrip(departure, arrival, route("summary").asInstanceOf[String], 
        steps)
    }
    
    def parseResponse(json: List[Map[String, _]]) = {           
      // Google might return several 'routes' objects but we just
      // deal with first one now
      // NOTE: To parse scala map object, we can also use .get()
      // method to avoid to crash when key doesn't exist, but
      // we believe in google service that always returns at least key params
      // so let's just use () and if something happens, we catch exception
      // from try catch and blame on google
      try {
        val route = json(0).asInstanceOf[Map[String, _]]
        Travel(toTrip(route), "local")  
      } catch  {
        case _ =>
          null          
      }
    }

    /**
     * From client or from google
     */
    override def receive = {    
      case PluginRequest(id, reply, reqs) =>
        var httpReqs = List[HttpRequest]()
        
        // try catch here and if something happens, let parent know 
        // something happened....
        val r = reqs.asInstanceOf[List[RequestMap[String, String]]]
        
        r.foreach({x=>           
          val params = x.params
          // Check validaty of the given params
          if (params.get("origin").orNull == null || params.get("destination").orNull == null) {
            // throw an error
            EventHandler.error(self, "either origin or destination param not exist")          
          }
                              
          var rparams = Map[String, Iterable[String]]("origin"->List[String](params("origin")), 
            "destination"->List[String](params("destination")), 
            "mode" -> List[String](getDirMode(params.getOrElse("mode", "car"))),
            "sensor" -> List[String](params.getOrElse("sensor", "false")),
            "alternatives"-> List[String](params.getOrElse("alternatives", "false")), 
            "units" -> List[String](params.getOrElse("units", "metric"))
          )
            
          if (params.get("region").orNull != null)
            rparams += "region" -> List[String](params("region"))
            
          if (params.get("avoid").orNull != null)
            rparams += "avoid" -> List[String](params("avoid"))
            
          if (params.get("waypoints").orNull != null)
            rparams += "waypoints" -> List[String](params("waypoints"))
            
          val req = new HttpRequest(x.id, apiUrl, rparams)
          httpReqs ::= req
        });
          
        parents += id -> reply
         
        // Schedule http client
        client ! Gets(id, httpReqs)
      
      // accept complete ack from the http client
      case Complete(id, resps) =>
        // Check response status code. If the code is not 200 OK,
        // just log the error and return fail.
        var success = false
        var results = Map[String, ResponseRes[Travel]]()
        
        resps.foreach({x=>
          var output = List[String]()  
          var travel:Travel = null
          
          success = if (x.code == 200) {
            val json = parse(x.content).values.asInstanceOf[Map[String, String]]
            val status = json("status")                     
            // Again check status of response content, if it is not 'OK'
            // just log the status and return fail
            if (status != "OK") {
              EventHandler.warning(
                this, "Google directions api returns bad status: " + status)
              false
            } else {  
              travel = parseResponse(
                 json("routes").asInstanceOf[List[Map[String, _]]])    
              if (travel == null) {
                EventHandler.warning(
                  this, "Google directions api returns invalid format response => \n" + 
                  x.content)
                false
              }                           
              true
            }
          } else { false }
          
          results += x.id -> new ResponseRes(x.id, success, travel)
        })

        // Find a parent actor which the result has to be passed
        var parent: ActorRef = parents.get(id).orNull
        if (parent != null) {
          // After found the actor, remove it from the list.          
          parent ! PluginResponse[ResponseRes[Travel]](id, pluginName, results)
          
          // TODO => later let's remove the item in future thread.
          parents -= id
        } else {
          // If not?, I don't know what happened here. just do nothing and
          // log about this situation
          EventHandler.error(self, "no parent actor for id: " + id)
        }     
    }   

    override def preStart = {} 

    override def postStop() = {}
  }
}

