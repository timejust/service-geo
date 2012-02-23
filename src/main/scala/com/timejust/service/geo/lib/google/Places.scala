package com.timejust.service.geo.lib.google
/**
 * Places.scala
 * Google place service implementation with asynchronous 
 * http client
 *
 * @author Min S. Kim (minsikzzang@gmail.com, minsik.kim@timejust.com)
 */ 

import akka.actor._
import akka.config.Config._ 
import akka.config.Supervision.OneForOneStrategy
import akka.config.Supervision.Permanent
import akka.event.EventHandler
import akka.routing.CyclicIterator
import akka.routing.Routing
import akka.routing.Routing.Broadcast
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.service.geo.lib.timejust._
import com.timejust.service.geo.lib.timejust.AsyncHttpClientPool._
import com.timejust.service.geo.actor.GeocodingEngine._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

/**
 * Implementation of google places api in asynchronous manner
 */
object Places {  
  case class Place(id: String, key: String, location: String, radius: String, 
    name: String = "", sensor: Boolean = false, keyword: String = "", 
    language: String = "", types: String = "")   
  class PlacedInfo(address: String, lat: Double, lng: Double) extends GeocodingInfo(address, lat, lng)
  case class PlaceResult(id: String, success: Boolean, results: List[PlacedInfo])  
  
  case class Request(id: String, reply: ActorRef, reqs: List[Place])  
  case class Response(id: String, resps: Map[String, PlaceResult])
         
  /**
   * Create the fetchers
   */
  val actors = Vector.fill(4)(Actor.actorOf[PlaceActor].start())
  
  /**
   * Wrap them with a load-balancing router
   */
  val place = Routing.loadBalancerActor(CyclicIterator(actors)).start()
  
  /**
   *
   */
  var parents: Map[String, ActorRef] = Map[String, ActorRef]()
  
  /**
   * Google place search api service url
   */
  val apiUrl = config.getString("google.place-api-url", 
    "https://maps.googleapis.com/maps/api/place/search/json")
  
  /**
   * Actor to request geo data to google api in asynchronous manner
   */
  class PlaceActor extends Actor {
    self.lifeCycle = Permanent
    // self.dispatcher = LowlevelWorkers.dispatcher
    // self.faultHandler = OneForOneStrategy(List(classOf[Throwable]), 5, 5000)
    
    /**
     * From client or from google
     */
    def receive = {              
      case Request(id, reply, reqs) =>
        var httpReqs = List[HttpRequest]()
        reqs.foreach({x=> 
          var params = Map[String, Iterable[String]](
            "key"->List[String](x.key), 
            "location"->List[String](x.location),
            "radius"->List[String](x.radius),
            "sensor"->List[String](if (x.sensor) {"true"} else {"false"}))
            
          if (x.keyword != "")
            params += "keyword" -> List[String](x.keyword)
          if (x.language != "")
            params += "language" -> List[String](x.language)
          if (x.name != "")
            params += "name" -> List[String](x.name)
          if (x.types != "")
            params += "types" -> List[String](x.types)
            
          println(params)
          val req = new HttpRequest(x.id, apiUrl, params)
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
        var results = Map[String, PlaceResult]()
        
        resps.foreach({x=>
          var output = List[PlacedInfo]()           
          success = if (x.code == 200) {
            val json = parse(x.content).values.asInstanceOf[Map[String, String]]
            val status = json("status")            
            // Again check status of response content, if it is not 'OK'
            // just log the status and return fail
            if (status != "OK") {
              EventHandler.warning(this, 
                "Google places api returns bad status: " + status)
              false
            } else {                 
              val rs = 
                json("results").asInstanceOf[List[Map[String, _]]]
              rs.foreach({r=>
                val geometry = 
                  r("geometry").asInstanceOf[Map[String, Map[String, Double]]]
                output ::= new PlacedInfo(
                  r.asInstanceOf[Map[String, String]]("vicinity"),
                  geometry("location")("lat"), geometry("location")("lng")) 
                })
              true
            }
          } else { false }
          
          results += x.id -> new PlaceResult(x.id, success, output)
        })

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
