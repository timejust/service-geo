package com.timejust.service.geo.lib.google

import akka.actor._
import akka.config.Supervision.OneForOneStrategy
import akka.config.Supervision.Permanent
import akka.event.EventHandler
import akka.routing.CyclicIterator
import akka.routing.Routing
import akka.routing.Routing.Broadcast
import bootstrap.timejust.LowlevelWorkers
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.service.geo.lib.timejust._
import com.timejust.service.geo.lib.timejust.AsyncHttpClientPool._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

/**
 * Implementation of google geocoding api in asynchronous manner
 */
object Geocoding { 
  class Geocode(id: String, address: String, latlng: String, bounds: String = "", 
    sensor: Boolean = false, region: String = "", language: String = "") {
    val id_ = id
    val address_ = address
    val latlng_ = latlng
    val bounds_ = bounds
    val sensor_ = sensor
    val region_ = region
    val language_ = language
  }
  
  class GeocodeResult(arg0: String, arg1: Boolean, arg2: List[String]) {
    val id = arg0
    var success = arg1
    var results = arg2
  }
  
  case class Request(id: String, reply: ActorRef, reqs: List[Geocode])  
  case class Response(id: String, resps: Map[String, GeocodeResult])
         
  /**
   * Create the fetchers
   */
  val actors = Vector.fill(4)(Actor.actorOf[GeocodingActor].start())
  
  /**
   * Wrap them with a load-balancing router
   */
  val geocoding = Routing.loadBalancerActor(CyclicIterator(actors)).start()
  
  /**
   *
   */
  var parents: Map[String, ActorRef] = Map[String, ActorRef]()
  
  /**
   *
   */
  val apiUrl = "http://maps.googleapis.com/maps/api/geocode/json"            
  
  /**
   * Actor to request geo data to google api in asynchronous manner
   */
  class GeocodingActor extends Actor {
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
          var params = Map[String, Iterable[String]]("address"->List[String](x.address_), 
            "sensor"->List[String]({if (x.sensor_) { "true" } else {"false" }}))
          if (x.latlng_ != "")
            params += "latlng" -> List[String](x.latlng_)
          if (x.bounds_ != "")
            params += "bounds" -> List[String](x.bounds_)
          if (x.region_ != "")
            params += "region" -> List[String](x.region_)
          if (x.language_ != "")
            params += "language" -> List[String](x.language_)
            
          val req = new HttpRequest(x.id_, apiUrl, params)
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
        var results = Map[String, GeocodeResult]()
        
        resps.foreach({x=>
          var output = List[String]()           
          success = if (x.code == 200) {
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
          } else { false }
          
          results += x.id -> new GeocodeResult(x.id, success, output)
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
