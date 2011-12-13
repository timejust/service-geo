package com.timejust.service.geo.lib.locomote

import akka.actor._
import akka.event.EventHandler
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.service.geo.lib.timejust.Plugin._
import com.timejust.service.geo.lib.timejust.DirectionPlugin._
import com.timejust.service.geo.lib.timejust.AsyncHttpClientPool._
import java.util.Calendar
import java.text.SimpleDateFormat
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import scala.collection.immutable.HashMap._

/**
 * Implementation of locomote location api in asynchronous manner
 */
object Locomote { 
  /**
   *
   */
  val apiUrl = "http://dev.isokron.com/1/directions.json"         
     
  val pluginName = "locomote"
  
  /**
   * Create the fetchers
   */
  val plugin = (new Factory()).createPlugin(4, Actor.actorOf[DirectionActor].start())
     
  class Factory extends PluginFactory {
    override def name() = {
      pluginName
    }
  }
  
  /**
   * Actor to request direction data to locomote api in asynchronous manner
   */ 
  class DirectionActor extends DirPluggableActor {    
    implicit val formats = DefaultFormats          
        
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
    
    override def getModeTrain(): String = { "railway" }
    override def getModeBus(): String = { "bus" }
    override def getModeDriving(): String = { "car" }
    override def getModeDefault(): String = { "railway" }
  
    def blankIfEmpty(s: Any): String = {
      if (s == null) { "" } else { s.asInstanceOf[String] }
    }

    def zeroIfEmpty(s: Any): Int = {
      if (s == null) { 0 } else { s.asInstanceOf[BigInt].intValue() }
    }
    
    def doubleZeroIfEmpty(s: Any): Double = {
      if (s == null) { 0.0 } else { s.asInstanceOf[Double] }
    }
    
    def toSchedule(map: Map[String, _]) = {
      Schedule(blankIfEmpty(map.get("time").orNull), 
        blankIfEmpty(map.get("name").orNull),
        doubleZeroIfEmpty(map.get("lat").orNull), 
        doubleZeroIfEmpty(map.get("lon").orNull))
    }
    
    def toDirection(m: Map[String, _]) = {
      Direction(toSchedule(m("departure").asInstanceOf[Map[String, _]]),
        toSchedule(m("arrival").asInstanceOf[Map[String, _]]),
        blankIfEmpty(m.get("mean").orNull), blankIfEmpty(m.get("line").orNull),
        blankIfEmpty(m.get("headsign").orNull), blankIfEmpty(m.get("network").orNull),
        blankIfEmpty(m.get("milestones").orNull), blankIfEmpty(m.get("walk").orNull), 
        0, zeroIfEmpty(m.get("duration").orNull), "", 
        blankIfEmpty(m.get("schedule").orNull), "", "")
    }
    
    def toSubsets(trips: List[Map[String, _]]) = {
      var mean_ = ""
      var directions = List[Direction]()
      var departure: Schedule = null
      var arrival: Schedule = null
      var duration: Int = 0
      var subsets = List[TripSubset]()
      
      trips.foreach(x=>{
        val direction = toDirection(x)  
        val mean = direction.mean        
        
        if (mean_ != mean) {          
          if (directions.length > 0) {
            // Create new subset if direction list exists,
            subsets ::= TripSubset(departure, arrival, duration, directions)
          }
          
          // Initialize duration, direction list and departure.
          duration = 0          
          departure = direction.departure                    
        }
          
        arrival = direction.arrival   
        
        // Add the current direction to the direction list                         
        directions ::= direction        
        
        // Accumulate the duration of direction
        duration += direction.duration
        mean_ = mean        
      })
      
      subsets
    }
    
    def toSets(trips: List[Map[String, _]]) = {
      var departure: Schedule = null
      var arrival: Schedule = null
      var duration: Int = 0
      val subsets = toSubsets(trips)
      
      subsets.foreach(x=>{
        if (duration == 0) {
          departure = x.departure
        }
        
        // Accumulate the duration of direction
        duration += x.duration
        arrival = x.arrival;          
      })
      
      List[TripSet](TripSet(departure, arrival, duration, subsets))
    }
    
    def toTrips(trips: List[Map[String, _]]) = {
      var departure: Schedule = null
      var arrival: Schedule = null
      var duration: Int = 0
      val sets = toSets(trips)
      
      sets.foreach(x=>{
        if (duration == 0) {
          departure = x.departure
        }
        
        // Accumulate the duration of direction
        duration += x.duration
        arrival = x.arrival;        
      })
      
      List[Trip](Trip(departure, arrival, duration, "", sets))
    }
    
    def parseResponse(json: Map[String, String]) = {       
      // NOTE: To parse scala map object, we have to use .get().isNull
      // method to avoid to crash when key doesn't exist, because
      // we don't fully believe in locomote api service. It sometimes returns
      // invalid json response. So we put safety functions in every map value.
      // Nevertheless, if something happens, let's blame on "locomote" :)
      try {
        val reps = json("trips").asInstanceOf[List[Map[String, _]]]
        Travel(toTrips(reps))
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
          if (params.get("origin").orNull == null || params.get("destination").orNull == null ||
            params.get("apikey").orNull == null || params.get("time").orNull == null || 
            params.get("mode").orNull == null) {
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
            "means" -> List[String](getDirMode(params("mode"))),
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
        var results = Map[String, ResponseRes[Travel]]()
        
        resps.foreach({x=>
          var output = List[String]()  
          var travel:Travel = null
          
          success = if (x.code == 200) {
            // println(x.content)
            val json = parse(x.content).values.asInstanceOf[Map[String, String]]
            val status = json("status")            
            // Again check status of response content, if it is not 'OK'
            // just log the status and return fail
            if (status != "ok") {
              EventHandler.warning(
                this, "Locomote api returns bad status: " + status)
              false
            } else {  
              travel = parseResponse(
                 json("directions").asInstanceOf[Map[String, String]])    
              if (travel == null) {
                EventHandler.warning(
                  this, "Locomote api returns invalid format response => \n" + 
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

