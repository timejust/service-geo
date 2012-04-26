package com.timejust.service.geo.endpoint
/**
 * Direction.scala
 * Geo-direction service endpoint class
 *
 * @author Min S. Kim (minsikzzang@gmail.com, minsik.kim@timejust.com)
 */ 

import akka.actor._
import akka.http._
import akka.event._
import akka.routing.CyclicIterator
import akka.routing.Routing
import com.timejust.service.geo.actor._
import com.timejust.service.geo.actor.DirectionEngine._
import com.timejust.service.geo.lib.timejust._
import com.timejust.service.geo.lib.timejust.DirectionPlugin._
import javax.ws.rs.core.MediaType  
import java.net.URLDecoder
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

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
  val directions = Vector.fill(8)(Actor.actorOf(new DirectionActor).start())

  // Wrap them with a load-balancing router
  val direction = Routing.loadBalancerActor(CyclicIterator(directions)).start()
    
  // service_url/v1/geo/direction?geo={url encoded address}
  def hook(uri: String): Boolean = ((uri == directionActor))
  
  // def provide(uri: String): ActorRef = Actor.actorOf[GeoRecognitionActor].start
  def provide(uri: String): ActorRef = direction

  // This is where you want to attach your endpoint hooks
  override def preStart() = {
    // We expect there to be one root and that it's already been started up
    // obviously there are plenty of other ways to obtaining this actor
    // the point is that we need to attach something (for starters anyway)
    // to the root
    val root = Actor.registry.actorsFor(classOf[RootEndpoint]).head
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
 *    id=1&origin=8 rue cauchy&destination=26 rue de longchamp&
 *    time=13121221212&mode={train,bus,driving}&base={arrival,departure}
 *
 * - Single request with geo position
 *  curl -v -X POST -d'[{"id":"0","origin":"48.8835582,2.2568579999999656","destination":"44.8403587,-0.5994221",
 "time":"1332140400","mode":"bus","base":"departure"},{"id":"1","origin":"48.8835582,2.2568579999999656",
 "destination":"44.8403587,-0.5994221","time":"1332140400","mode":"train","base":"departure"},
 {"id":"2","origin":"48.8835582,2.2568579999999656","destination":"44.8403587,-0.5994221","time":"1332140400",
 "mode":"car","base":"departure"}]' 'http://127.0.0.1:9000/service-geo/v1/geo/direction'
 *
 * - Multiple request with address
 *  json -> 
 * [{"id":"1",
 *   "origin":"8 rue cauchy",
 *   "destination":"26 rue de longchamp",
 *   "time":"1232132",
 *   "mode":"train"},
 *  {"id":"2",
 *   "origin":"8 rue cauchy",
 *   "destination":"26 rue de longchamp",
 *   "time":"1232132",
 *   "mode":"bus"}]
 * 
 */
class DirectionActor extends Actor {
  def decodeUTF8(in: String) = {
    if (in != null)
      URLDecoder.decode(in, "UTF-8")
    else
      ""
  }

  def receive = {    
    case post:Post =>
      post.response.setContentType(MediaType.APPLICATION_JSON)
      post.response.setCharacterEncoding("UTF-8")
      var dirReqList = List[DirReq]()
      var badRequest = ("status" -> "bad_request") ~ ("results" -> "")
      var mode = modeDriving
      var base = baseDeparture
      
      if (post.request.getContentLength <= 0) {
        post.OK(Printer.compact(JsonAST.render(badRequest)))
      } else {
        var req = post.request.getReader().readLine 
        if (req != null) {                        
          // Parse the given json strings and convert to list of Geo
          // object format.
          try {
            val json = parse(req).values.asInstanceOf[List[Map[String, String]]]
            json.reverse.foreach(x => {
              mode = if (x.get("mode").orNull == null) { 
                  modeDriving } else { x("mode") }
              base = if (x.get("base").orNull == null) { 
                  baseDeparture } else { x("base") }
              val dirReq = DirReq(
                  x("id"), x("origin"), x("destination"), x("time"), mode, base)
              if (dirReq != null) {
                dirReqList ::= dirReq
              }
            })              
          } catch {
            case _ =>
              post.OK(Printer.compact(JsonAST.render(badRequest)))
          }                
        } else {
          post.OK(Printer.compact(JsonAST.render(badRequest)))
        }
      }
            
      if (dirReqList.size > 0) {
        // Call geocodingActor to process geo recognition task with
        // the given geo input and request
        directionActor ! DirRequest(dirReqList, post)
      }
    
    // Handle get request
    case get:Get => 
      get.response.setContentType(MediaType.APPLICATION_JSON)
      val id = get.request.getParameter("id")
      val origin = get.request.getParameter("origin")
      val destination = get.request.getParameter("destination")
      val time = get.request.getParameter("time")
      var mode = get.request.getParameter("mode")
      var base = get.request.getParameter("base")
      var dirReqList = List[DirReq]()
      var badRequest = ("status" -> "bad_request") ~ ("results" -> "")
      
      if (id == null || origin == null || destination == null || 
          time == null) { 
        get.OK(Printer.compact(JsonAST.render(badRequest)))        
      } else {
        // If mode param is not given, we just assume mode is driving
        if (mode == null)
          mode = modeDriving          
        if (base == null)
          base = baseDeparture
          
        dirReqList ::= DirReq(id, origin, destination, time, mode, base)
      }
      
      if (dirReqList.size > 0) {
        // Call directionActor to process finding direction task with
        // the given direction input and request
        directionActor ! DirRequest(dirReqList, get)
      }  
      
    case other:RequestMethod => other NotAllowed "unsupported request"
  }
}
