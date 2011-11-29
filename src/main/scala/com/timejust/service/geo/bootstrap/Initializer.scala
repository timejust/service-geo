package com.timejust.geo.bootstrap

import akka.util.AkkaLoader
import akka.actor.BootableActorLoaderService
import javax.servlet.{ServletContextListener, ServletContextEvent}

class Initializer extends ServletContextListener {
  lazy val loader = new AkkaLoader
  def contextDestroyed(e: ServletContextEvent): Unit = loader.shutdown
  def contextInitialized(e: ServletContextEvent): Unit = loader.boot(true, new BootableActorLoaderService {})
}
