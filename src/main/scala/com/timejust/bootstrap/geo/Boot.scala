package com.timejust.bootstrap.geo

import akka.actor._
import akka.http._
import akka.config._
import akka.config.Supervision._
import akka.dispatch.Dispatchers
import akka.util._
import com.timejust.service.geo.endpoint._
 
class Boot {
  val Factory = SupervisorFactory(SupervisorConfig(OneForOneStrategy(List(classOf[Exception]), 3, 100),
                                                   Supervise(Actor.actorOf[RootEndpoint], Permanent) ::
                                                   Supervise(Actor.actorOf[Recognition], Permanent) :: 
                                                   Supervise(Actor.actorOf[Direction], Permanent) :: 
                                                   Supervise(Actor.actorOf[IPGrabber], Permanent) :: Nil))

  Factory.newInstance.start
}

object Worker {
  val dispatcher =
    Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("worker_pool")
      .setCorePoolSize(4)
      .setMaxPoolSize(16)
      .setKeepAliveTimeInMillis(60000)
      .build
}

object LowlevelWorkers {
  val dispatcher =
    Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("low_worker_pool")
      .setCorePoolSize(4)
      .setMaxPoolSize(16)
      .setKeepAliveTimeInMillis(60000)
      .build
}

