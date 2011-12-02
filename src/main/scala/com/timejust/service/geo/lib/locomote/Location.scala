package com.timejust.service.geo.lib.locomote

import akka.actor._
import akka.event.EventHandler
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.service.geo.lib.timejust.Plugin._
import com.timejust.service.geo.lib.timejust.AsyncHttpClientPool._
import java.util.Calendar
import java.text.SimpleDateFormat
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

/**
 * Implementation of locomote location api in asynchronous manner
 */
object Locomote { 
  /**
   *
   */
  val apiUrl = "http://dev.isokron.com/1/directions.json"
         
     
  /**
   * Create the fetchers
   */
  val plugin = (new Factory()).createPlugin(4, Actor.actorOf[DirectionActor].start())
     
  class Factory extends PluginFactory {
    override def name() = {
      "locomote"
    }
  }
  
  /**
   * Actor to request direction data to locomote api in asynchronous manner
   */
  // class DirectionActor extends Actor {    
  class DirectionActor extends PluggableActor {        
    def unixToDateString(unix: String) = {
      val c = Calendar.getInstance()
      
      if (unix != null) {
        c.setTimeInMillis(System.currentTimeMillis())  
      } else {
        // If the given unix time is null, get current time stamp
        c.setTimeInMillis(unix.toLong)  
      }          
      
      val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
      sdf.format(c.getTime())      
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
          if (params.get("origin").orNull == null || params.get("destination").orNull == null ||
            params.get("apikey").orNull == null || params.get("time").orNull == null) {
            // throw an error
          }
          
          // We can assure that all the given params are available now
          // if code reaches here
          // Parse origin and destination data to latitude and 
          // longitude values
          val origin = params("origin").split(",")
          val destination = params("destination").split(",")
                    
          // Convert unix time to readable date format and you should make sure
          // the given timestamp is gmt +0  
          var rparams = Map[String, Iterable[String]]("apikey"->List[String](params("key")), 
            "departure_lat"->List[String](origin(0)), 
            "departure_lon" -> List[String](origin(1)),
            "destination_lat"-> List[String](destination(0)), 
            "destination_lon" -> List[String](destination(1)),
            "time" -> List[String](unixToDateString(params("time"))),
            "area" -> List[String]("paris"),
            "means" -> List[String]("metro","bus","bike","railway","car"),
            "lead" -> List[String]("ARRIVAL")
          )
            
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
        // var results = Map[String, DirectionResult]()
        
        resps.foreach({x=>
          var output = List[String]()           
          success = if (x.code == 200) {
            println(x.content)
            
            true
            /*
            val json = parse(x.content).values.asInstanceOf[Map[String, String]]
            val status = json("status")            
            // Again check status of response content, if it is not 'OK'
            // just log the status and return fail
            if (status != "OK") {
              EventHandler.warning(this, 
                "Google geocoding api returns bad status: " + status)
              false
            } else {                 
              val rs = 
                json("results").asInstanceOf[List[Map[String, String]]]
              rs.foreach({r=>output ::= r("formatted_address")})
              true
            }
            */
          } else { false }
          
          /// results += x.id -> new GeocodeResult(x.id, success, output)
        })
        /*
        // Find a parent actor which the result has to be passed
        var parent: ActorRef = parents.get(id).orNull
        if (parent != null) {
          // After found the actor, remove it from the list.          
          parent ! Response(id, results)
          
          // TODO => later let's remove the item in future thread.
          parents -= id
        } else {
          // If not?, I don't know what happened here. just do nothing and
          // log about this situation
          EventHandler.error(self, "no parent actor for id: " + id)
        }        
        */
    }   

    /** 
     * Linking up our actors with this http client as to supervise them 
     */
    // override def preStart = actors foreach { self.startLink(_) }

    /**
     * When we are stopped, stop our team of  and our router
     */
    // override def postStop() {
      // Unlinked all fetchers
    //  actors.foreach(self.unlink(_))

      // Send a PoisonPill to all fetchers telling them to shut down themselves
      // router ! Broadcast(PoisonPill)

      // Send a PoisonPill to the router, telling him to shut himself down
      // router ! PoisonPill
    // }
  }
}

