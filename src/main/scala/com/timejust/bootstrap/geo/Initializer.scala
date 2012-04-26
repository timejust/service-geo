package com.timejust.bootstrap.geo

import akka.util.AkkaLoader
import akka.actor.BootableActorLoaderService
// import akka.remote.BootableRemoteActorService
import javax.servlet.{ServletContextListener, ServletContextEvent}

/**
  * This class can be added to web.xml mappings as a listener to start and postStop Akka.
  *<web-app>
  * ...
  *  <listener>
  *    <listener-class>com.my.Initializer</listener-class>
  *  </listener>
  * ...
  *</web-app>
  */
class Initializer extends ServletContextListener {
  lazy val loader = new AkkaLoader
  def contextDestroyed(e: ServletContextEvent): Unit = loader.shutdown
  def contextInitialized(e: ServletContextEvent): Unit = 
    loader.boot(true, new BootableActorLoaderService {})
    // loader.boot(true, new BootableActorLoaderService with BootableRemoteActorService) //<-- Important
  //     loader.boot(true, new BootableActorLoaderService {}) // If you don't need akka-remote
}
