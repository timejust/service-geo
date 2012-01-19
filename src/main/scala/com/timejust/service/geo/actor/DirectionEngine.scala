package com.timejust.service.geo.actor

import akka.actor._
import akka.config.Config._ 
import akka.config.Supervision.OneForOneStrategy
import akka.config.Supervision.Permanent
import akka.dispatch.Dispatchers
import akka.event.EventHandler
import akka.http.RequestMethod
import akka.routing.Routing.Broadcast
import akka.routing.CyclicIterator
import akka.routing.Routing
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.bootstrap.geo.Worker
import com.timejust.service.geo.lib._
import com.timejust.service.geo.lib.locomote._
import com.timejust.service.geo.lib.timejust._
import com.timejust.service.geo.lib.timejust.DirectionPlugin._
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
    time: String, mode: String, base: String, plugin: String = "locomote");
    
  class DirectionApi(arg0: String, arg1: RequestMethod) {
    val id = arg0
    val request = arg1
    var plugins = Set[String]()
    var results = List[(String, JsonAST.JObject)]()
    
    def push(plugin: String) = {
      plugins += plugin
    }
    
    def pop(plugin: String) = {
      plugins -= plugin
    }
    
    def get(plugin: String) = {
      plugins.contains(plugin)
    }
    
    def size = {
      plugins.size
    }
  }
    
  sealed trait Message
  
  /**
   * Event signal from endpoint to worker actor
   */
  case class DirRequest(dirList: List[DirReq], request: RequestMethod) extends Message

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
      Map[String, PluginRef](
        ("locomote" -> Locomote.plugin),
        ("google.directions" -> google.Directions.plugin))
    
    def append(name: String, option: Boolean, source: String) = {
      plugins ::= Map[String, (Boolean, String)](name -> (option, source))
    }
    
    def getPlugin(name: String) = {
      pluginClasses.get(name).orNull
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
      var plugin: PluginRef = null
      
      if (isOriginPos && isDestinationPos) {
        plugins.foreach({x=>
          x.foreach({p=>
            if (p._2._2 == "position") {
              plugin = pluginClasses.get(p._1).orNull
            }              
          })
        })
      } else if (!isOriginPos && !isDestinationPos) {
        // get ratp or google plugin if possible
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
    
    var dirResps = Map[String, DirectionApi]()
            
    def receive = {    
      // Handle get request      
      case DirRequest(dirList, request) =>
        val reqId = System.currentTimeMillis().toString()              
        var status = ""
        var reqsMap = Map[PluginRef, List[RequestMap[String, String]]]()
        var plugin: PluginRef = null
        var dirApi = new DirectionApi(reqId, request)
        
        dirList.foreach(x => {          
          status = "ok"          
          plugin = null
                              
          // If the given direction mode is drving, we prefer to use google
          // direction api rather than using any existing plugins.
          if (x.mode == DirectionPlugin.modeDriving) {
            plugin = directionPlugins.getPlugin("google.directions")            
          }
          // If google direction plugin is not available, let the framework
          // decide which plugin to use.
          
          if (plugin == null)
            plugin = directionPlugins.getPlugin(x.origin, x.destination)
            
          if (plugin == null) {
            status = "missing_plugin"
          } else {    
            val params = Map[String, String]("key" -> "TSFEkeeXKW0DSBjA9npa", 
              "origin" -> x.origin, "destination" -> x.destination, 
              "time" -> x.time, "mode" -> x.mode, "base" -> x.base)
            
            var reqsList = reqsMap.get(plugin).orNull
            if (reqsList == null) {              
              reqsList = List(RequestMap[String, String](x.id, params))                  
              dirApi.push(plugin.name)   
            } else {              
              reqsList ::= RequestMap[String, String](x.id, params)
            }                
            reqsMap += (plugin -> reqsList)            
          }
        })   
            
        if (reqsMap.size > 0) {
          dirResps += reqId -> dirApi
        }
        
        reqsMap.foreach(x => {
          x._1.actorRef ! PluginRequest[RequestMap[String, String]](
            reqId, self, x._2)             
        })
        
      case PluginResponse(id, name, resps) =>
        val dirApi: DirectionApi = dirResps.get(id).orNull
        var dirRespList = dirApi.results
        var results = List[(String, JsonAST.JObject)]()
        var status = "ok"
          
        if (dirApi == null || dirApi.request == null) {
          // No get object, something really bad happened.
          // just clear memories and do nothing.  
          dirResps -= id   
          EventHandler.error(self, "cannot find get object associated id - " + id)          
        } else {                              
          resps.foreach({case (k: String, v: ResponseRes[Travel])=>
            status = 
              if (v.success && v.results != null) { "ok" } 
              else { "plugin error - " + name }                                    
            if (status == "ok") {
              results ::= (k -> 
                ("status" -> status) ~ ("format" -> v.results.format) ~ 
                v.results.toJObject)  
            } else {
              results ::= (k -> 
                ("status" -> status) ~ ("format" -> "") ~ 
                ("trip" -> ""))
            }            
          })                      
          dirRespList = dirRespList ::: results       
        }
        
        if (dirApi != null) {
          dirApi.pop(name)   
          if (dirApi.request != null && dirApi.size == 0) {
            val json = ("status" -> status) ~ ("results" -> dirRespList)
            dirApi.request.OK(compact(JsonAST.render(json)))
            dirResps -= id   
          } else {
            // if we don't send back results yet save them for the future
            dirApi.results = dirRespList
          }         
        }        
    }
  }
}