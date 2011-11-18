package com.timejust.service

import akka.actor._
import akka.http._
import akka.config._
import akka.config.Supervision._
import akka.util._

 
class Boot {
  val factory = SupervisorFactory(SupervisorConfig(OneForOneStrategy(List(classOf[Exception]), 3, 100),
                                                   Supervise(Actor.actorOf[RootEndpoint], Permanent) ::
                                                   Supervise(Actor.actorOf[GeoRecognition], Permanent) :: Nil))

  factory.newInstance.start
}


