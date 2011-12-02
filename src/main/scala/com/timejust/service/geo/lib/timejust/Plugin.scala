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
  case class ResponseList[T](id: String, success: Boolean, results: List[T])
  case class ResponseMap[K, V](id: String, success: Boolean, results: Map[K, V])
  
  case class PluginRequest[T](id: String, reply: ActorRef, reqs: List[T])  
  // case class PluginResponse(id: String, resps: Map[String, DirectionResult])
  
  abstract class PluginFactory() {
    var plugins = Map[String, ActorRef]()
    
    def name(): String
    
    def createPlugin(num: Int, actor: ActorRef): ActorRef = {
      var plugin = findPlugin()
      if (plugin == null) {
        val actors = Vector.fill(num)(actor)
        plugin = Routing.loadBalancerActor(CyclicIterator(actors)).start()
        append(plugin)
      }
      
      plugin      
    }
    
    def append(actor: ActorRef) = {
      plugins += (name() -> actor)
    }  
    
    def findPlugin() = {
      plugins.get(name()) orNull
    }
  }
  
  abstract class PluggableActor extends Actor {
    self.lifeCycle = Permanent
    /**
     *
     */
    var parents: Map[String, ActorRef] = Map[String, ActorRef]()    
  }
}
