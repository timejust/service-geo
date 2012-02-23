package com.timejust.service.geo.endpoint
/**
 * IPGrabber.scala
 * Geo-ipgrabber service endpoint class
 *
 * @author Min S. Kim (minsikzzang@gmail.com, minsik.kim@timejust.com)
 */ 

import akka.actor._
import akka.http._
import akka.event._
import akka.routing.CyclicIterator
import akka.routing.Routing
import net.liftweb.json._
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import com.timejust.service.geo.actor._
import com.timejust.service.geo.actor.GeocodingEngine._
import com.timejust.service.geo.lib.timejust._
import javax.ws.rs.core.MediaType  
  
/**
 * End point class for geo ip grabber
 */
class IPGrabber extends Actor with Endpoint {
  final val version = "/service-geo/v1/"
  final val geo = "geo/"
  final val ipGrabberEndpoint = version + geo + "ip"
  
  // Use the configurable dispatcher
  self.dispatcher = Endpoint.Dispatcher
      
  // Create the recognition actors
  val ipGrabbers = Vector.fill(2)(Actor.actorOf(new IPGrabberActor).start())

  // Wrap them with a load-balancing router
  val ipGrabber = Routing.loadBalancerActor(CyclicIterator(ipGrabbers)).start()
    
  // service_url/v1/geo/recognition?geo={url encoded address}
  def hook(uri: String): Boolean = ((uri == ipGrabberEndpoint))
  
  // def provide(uri: String): ActorRef = Actor.actorOf[GeoRecognitionActor].start
  def provide(uri: String): ActorRef = ipGrabber
  
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
 * @brief IPGrabberActor service handler to respond to some HTTP requests
 *
 * Sample: 
 * - Request ->
 *  http://service.timejust.com/v1/geo/ip
 *
 * - Response -> 
 *  {
 *    "status":"ok",
 *    "ip":"192.168.5.12"
 *  }
 */
class IPGrabberActor extends Actor {
  
  def receive = {    
    // Handle get request
    case get:Get =>       
      get.response.setContentType(MediaType.APPLICATION_JSON)
      get.response.setCharacterEncoding("UTF-8")
      
      val callback = get.request.getParameter("callback")
      val serviceUnavailable = "service_unavailable"
      var ip = get.request.getRemoteAddr()
      var status = "ok"      
      var response = ""
            
      if (ip == null) {
        ip = ""
        status = serviceUnavailable
      }
      
      val json = ("status" -> status) ~ ("ip" -> ip)                
      if (callback != null) {
        get.response.setContentType("application/javascript")
        response = callback + "(" + Printer.compact(JsonAST.render(json)) + ");"
      } else {
        response = Printer.compact(JsonAST.render(json))
      }      
            
      get.OK(response)        
                
    case other:RequestMethod => other NotAllowed "unsupported request"
  }
}
