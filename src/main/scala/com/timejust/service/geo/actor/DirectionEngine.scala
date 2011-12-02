package com.timejust.service.geo.actor

import akka.actor._
import akka.config.Config._ 
import akka.config.Supervision.OneForOneStrategy
import akka.config.Supervision.Permanent
import akka.dispatch.Dispatchers
import akka.event.EventHandler
import akka.http.Get
import akka.routing.Routing.Broadcast
import akka.routing.CyclicIterator
import akka.routing.Routing
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.bootstrap.geo.Worker
import com.timejust.service.geo.lib._
import com.timejust.service.geo.lib.locomote._
import com.timejust.service.geo.lib.timejust._
import com.timejust.service.geo.lib.timejust.Plugin._
import java.net.URLEncoder
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._

/**
 * Timejust finding direction proxy engine. We can plug-in any transportation
 * model here. 
 */
object DirectionEngine {
 /**
  * Direction request holder which contains id, origin, destination, time,
  * and transportation model which has be used.
  */
  case class DirReq(id: String, origin: String, destination: String, 
    time: String, plugin: String = "locomote");
    
  sealed trait Message
  
  /**
   * Event signal from endpoint to worker actor
   */
  case class DirRequest(dirList:List[DirReq], get:Get) extends Message

  // Create the Geocoding actors
  val actors = Vector.fill(4)(Actor.actorOf[DirectionHandler].start())

  // Wrap them with a load-balancing router
  val directionActor = Routing.loadBalancerActor(CyclicIterator(actors)).start()
  
  // Load up direction plugin configuration from akka.confs
  val directionPlugins = {
    val plugins = config.getList("service-geo.direction.plugins.names")
    val options = config.getList("service-geo.direction.plugins.options")   
    val sources = config.getList("service-geo.direction.plugins.sources")   
    var dPlugins = new DirectionPlugins()
    var i = 0
    
    plugins.map({x=>
      dPlugins.append(x, {  
        ((if (options.size > i) { options(i) } else { "off" }) == "on")
      }, { 
        if (sources.size > i) { sources(i) } else { "" }
      })
      i += 1
    }) 
    
    dPlugins   
  }
  
  class DirectionPlugins() {
    var plugins = List[Map[String, (Boolean, String)]]()
    val pluginClasses = 
      Map[String, ActorRef]("locomote" -> Locomote.plugin)
    
    def append(name: String, option: Boolean, source: String) = {
      plugins ::= Map[String, (Boolean, String)](name -> (option, source))
    }
    
    def getPlugin(origin: String, destination: String) = {
      // Detect float value in origin or destination
      // If they both are float values, let's find position type direction
      // plugin. If they both are strings, let's find address type 
      // direction plugin. Otherwise, return null which is a bad request.
      val regFloat = """(([1-9]+\.[0-9]*)|([1-9]*\.[0-9]+)|([1-9]+))"""

      // Position detection regex, position value consists of two float values
      // with comma between them. ex) 24.2332,44,2322
      val regPosition = regFloat + "(,)" + regFloat
      val isOriginPos = origin.matches(regPosition)
      val isDestinationPos = origin.matches(regPosition)
      var plugin: ActorRef = null
      
      if (isOriginPos && isDestinationPos) {
        plugins.foreach({x=>
          x.foreach({p=>
            if (p._2._2 == "position") {
              plugin = pluginClasses.get(p._1).orNull
            }              
          })
        })
      } else if (!isOriginPos && !isDestinationPos) {
        null
      } else {
        // error
        null
      }
      
      plugin      
    }
  }

  /**
   * Direction handler parses input request and pass data model to external
   * service provide to find the fastest direction depends on timejust policy.    
   */
  class DirectionHandler() extends Actor {   
    self.lifeCycle = Permanent  
    
    /*
    var json = List[Map[String, Map[String, String]]]()
    var geoResps = Map[String, GeocodingApi]()
    
    val reqNone = 0
    val reqGoogleGeocoding = 1
    val reqGooglePlace = 2
    */
            
    def receive = {    
      // Handle get request      
      case DirRequest(dirList, get) =>
        var id = ""
        var status = ""
      
        dirList.foreach(x => {
          id = x.id
          status = "ok"
                    
          val plugin = directionPlugins.getPlugin(x.origin, x.destination)
          if (plugin == null) {
            status = "missing_plugin"
          } else {
            val params = Map[String, String]("key" -> "TSFEkeeXKW0DSBjA9npa", 
              "origin" -> x.origin, "destination" -> x.destination, 
              "time" -> x.time)
            val maps = List[RequestMap[String, String]](
              RequestMap[String, String](id, params))
            plugin ! PluginRequest[RequestMap[String, String]](id, self, maps)
          }
        })                
    }
  }
}