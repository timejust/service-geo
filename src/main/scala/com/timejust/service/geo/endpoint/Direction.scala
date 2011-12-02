package com.timejust.service.geo.endpoint

import akka.actor._
import akka.http._
import akka.event._
import akka.routing.CyclicIterator
import akka.routing.Routing
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import com.timejust.service.geo.actor._
import com.timejust.service.geo.actor.DirectionEngine._
import com.timejust.service.geo.lib.timejust._
import javax.ws.rs.core.MediaType  


/**
 * End point class for geo direction api
 */
class Direction extends Actor with Endpoint {
  final val version = "/service-geo/v1/"
  final val geo = "geo/"
  final val directionActor = version + geo + "direction"
  
  // Use the configurable dispatcher
  self.dispatcher = Endpoint.Dispatcher
      
  // Create the recognition actors
  val directions = Vector.fill(4)(Actor.actorOf(new DirectionActor).start())

  // Wrap them with a load-balancing router
  val direction = Routing.loadBalancerActor(CyclicIterator(directions)).start()
    
  // service_url/v1/geo/direction?geo={url encoded address}
  def hook(uri: String): Boolean = ((uri == directionActor))
  
  // def provide(uri: String): ActorRef = Actor.actorOf[GeoRecognitionActor].start
  def provide(uri: String): ActorRef = direction
  
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
 * @brief Geo Direction service handler to respond to some HTTP requests
 *
 * Sample: 
 * - Single request with address
 *  http://service.timejust.com/v1/geo/direction?
 *    id=1&origin=8 rue cauchy&destination=26 rue de longchamp&time=13121221212
 *
 * - Single request with geo position
 *  http://service.timejust.com/v1/geo/direction?
 *    id=1&origin=2.3458,48.8289&destination=2.3522,42118.8434&time=13121221212
 *
 * - Multiple request with address
 *  json -> 
 * [{"id":"1",
 *   "origin":"8 rue cauchy",
 *   "destination":"26 rue de longchamp",
 *   "time":"1232132"},
 *  {"id":"2",
 *   "origin":"8 rue cauchy",
 *   "destination":"26 rue de longchamp",
 *   "time":"1232132"}]
 * 
 */
class DirectionActor extends Actor {
  // implicit val formats = DefaultFormats
  
  def receive = {    
    // Handle get request
    case get:Get => 
      get.response.setContentType(MediaType.APPLICATION_JSON)
      val id = get.request.getParameter("id")
      val origin = get.request.getParameter("origin")
      val destination = get.request.getParameter("destination")
      val time = get.request.getParameter("time")
      var dirReqList = List[DirReq]()
      var badRequest = ("status" -> "bad_request") ~ ("results" -> "")
      
      if (id == null || origin == null || destination == null || 
          time == null) {
        if (get.request.getContentLength <= 0) {
          get.OK(Printer.compact(JsonAST.render(badRequest)))
        } else {
          var req = get.request.getReader().readLine          
          if (req != null) {                        
            // Parse the given json strings and convert to list of Geo
            // object format.
            try {
              /*
              val json = parse(req).values.asInstanceOf[List[Map[String, String]]]
              json.reverse.foreach(x => {
                val geoReq = GeoReq(x.get("id").orNull, 
                  x.get("geo").orNull, x.get("src").orNull)
                if (geoReq != null) {
                  geoReqList ::= geoReq
                }
              })
              */              
            } catch {
              case e: JsonParser.ParseException =>
                get.OK(Printer.compact(JsonAST.render(badRequest)))
            }                
          } else {
            get.OK(Printer.compact(JsonAST.render(badRequest)))
          }
        }        
      } else {
        dirReqList ::= DirReq(id, origin, destination, time)
      }      
      
      if (dirReqList.size > 0) {
        // Call directionActor to process finding direction task with
        // the given direction input and request
        directionActor ! DirRequest(dirReqList, get)
        get.OK("HAHA")
      }      
      
    case other:RequestMethod => other NotAllowed "unsupported request"
  }
}
