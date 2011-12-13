package com.timejust.service.geo.lib.timejust

import akka.actor._
import akka.config.Supervision.OneForOneStrategy
import akka.config.Supervision.Permanent
import akka.routing.CyclicIterator
import akka.routing.Routing
import akka.routing.Routing.Broadcast


object Plugin {
  // case class DirectionResult(id: String, success: Boolean, results: List[String])
  case class RequestMap[K, V](id: String, params: Map[K, V])
  // case class ResponseList[T](id: String, success: Boolean, results: List[T])
  // case class ResponseMap[K, V](id: String, success: Boolean, results: Map[K, V])
  case class ResponseRes[T](id: String, success: Boolean, results: T)
  
  case class PluginRequest[T](id: String, reply: ActorRef, reqs: List[T])  
  case class PluginResponse[T](name: String, id: String, resps: Map[String, T])
  
  abstract class PluginFactory() {
    // var plugins = Map[String, PluggableActor]()
    var plugins = Map[String, PluginRef]()
    
    def name(): String        

    def createPlugin(num: Int, actorRef: ActorRef): PluginRef = {
      var plugin: PluginRef = findPlugin()
      if (plugin == null) {        
        val actors = Vector.fill(num)(actorRef)
        plugin = 
          PluginRef(name(), Routing.loadBalancerActor(CyclicIterator(actors)).start())
        append(plugin)
      }
      
      plugin      
    }
    
    def append(actor: PluginRef) = {
      plugins += (name() -> actor)
    }  
    
    def findPlugin() = {
      plugins.get(name()) orNull
    }
  }
  
  case class PluginRef(name: String, actorRef: ActorRef)
  
  abstract class PluggableActor() extends Actor {
    self.lifeCycle = Permanent
    /**
     *
     */
    var parents: Map[String, ActorRef] = Map[String, ActorRef]()    
  }
}
