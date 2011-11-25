package com.timejust.service.geo.endpoint

import akka.actor._
import akka.http._
import akka.event._
import akka.routing.CyclicIterator
import akka.routing.Routing
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import com.timejust.service.geo.actor._
import com.timejust.service.geo.actor.GeocodingEngine._
import com.timejust.service.geo.lib.timejust._


/**
 * End point class for geo recognition api
 */
class Recognition extends Actor with Endpoint {
  final val version = "/v1/"
  final val geo = "geo/"
  final val recognitionActor = version + geo + "recognition"
  
  // Use the configurable dispatcher
  self.dispatcher = Endpoint.Dispatcher
      
  // Create the recognition actors
  val recognitions = Vector.fill(4)(Actor.actorOf[RecognitionActor].start())

  // Wrap them with a load-balancing router
  val recognition = Routing.loadBalancerActor(CyclicIterator(recognitions)).start()
  // val recognition = Actor.actorOf[RecognitionActor].start()
    
  // service_url/v1/geo/recognition?geo={url encoded address}
  def hook(uri: String): Boolean = ((uri == recognitionActor))
  
  // def provide(uri: String): ActorRef = Actor.actorOf[GeoRecognitionActor].start
  def provide(uri: String): ActorRef = recognition
  
  /* 
  def hook(uri: String): Boolean = ((uri == recognitionActor))
  def provide(uri: String): ActorRef = {
    if (uri == recognitionActor)
      Actor.actorOf[GeoRecognitionActor].start
    else 
      actorOf[BoringActor].start()
  } 
  */

  // This is where you want to attach your endpoint hooks
  override def preStart() = {
    // We expect there to be one root and that it's already been started up
    // obviously there are plenty of other ways to obtaining this actor
    // the point is that we need to attach something (for starters anyway)
    // to the root
    val root = Actor.registry.actorsFor(classOf[RootEndpoint]).head
    // root ! Endpoint.Attach(hook, provide)
    root ! Endpoint.Attach(hook, provide)    
  }

  // since this actor isn't doing anything else (i.e. not handling other messages)
  //  just assign the receive func like so...
  // otherwise you could do something like:
  //  def myrecv = {...}
  //  def receive = myrecv orElse _recv
  def receive = handleHttpRequest
}

/**
 * @brief GeoRecognition service handler to respond to some HTTP requests
 *
 * request format json -> 
 *   [{"id":"1","geo":"rue cauchy","src":"192.168.0.21"},
 *    {"id":"2","geo":"26 rue longchamp","src":"192.168.0.21"}]
 * response format json -> 
 *   [{"id":"1","result":{"status":"ok","geo":["rue cauchy"]}},
 *    {"id":"2","result":{"status":"invalid request"}}]
 */
class RecognitionActor extends Actor {
  import javax.ws.rs.core.MediaType  
  implicit val formats = DefaultFormats
  
  def receive = {    
    // Handle get request
    case get:Get => 
      get.response.setContentType(MediaType.APPLICATION_JSON)
      var req = get.request.getParameter("geo")
      if (req == null) {
        if (get.request.getContentLength <= 0) {
          get.BadRequest("you need to put geo value")
        } else {
          req = get.request.getReader().readLine
        }        
      } 
      
      if (req != null) {
        var geoReqList = List[GeoReq]()
        // Parse the given json strings and convert to list of Geo
        // object format.
        try {
          val json = parse(req).values.asInstanceOf[List[Map[String, String]]]
          json.reverse.foreach(x => {
            val geoReq = new GeoReq(x.get("id").orNull, x.get("geo").orNull, x.get("src").orNull)
            if (geoReq != null) {
              geoReqList ::= geoReq
            }
          })

          // Call geocodingActor to process geo recognition task with
          // the given geo input and request
          geocodingActor ! GeoRequest(geoReqList, get)  
        } catch {
          case e: JsonParser.ParseException =>
            get.BadRequest("geo parameter cannot be parsed")
        }                
      }
      // implicit val formats = DefaultFormats
      // val ggg = parse(req).extract[Geo]
    case other:RequestMethod => other NotAllowed "unsupported request"
  }
}
