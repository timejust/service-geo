package com.timejust.util

/**
 * Logger.scala
 * Custom logging class with akka's EventHandler
 *
 * @author Min S. Kim (minsikzzang@gmail.com, minsik.kim@timejust.com)
 */ 

import akka.actor._
import akka.event.EventHandler

object Logger {
  val actor = Actor.actorOf[LoggingActor].start();
  
  def info(log: String) = {
    info_(actor, log)
  }
  
  def error(log: String) = {
    error_(actor, log)
  }
  
  def debug(log: String) = {
    debug_(actor, log)
  }
  
  def warn(log: String) = {
    warn_(actor, log)
  }  
  
  def info_(ref: ActorRef, log: String) = {
    EventHandler.info(ref, log);
  }

  def warn_(ref: ActorRef, log: String) = {
    EventHandler.warning(ref, log);
  }
  
  def error_(ref: ActorRef, log: String) = {
    EventHandler.error(ref, log);
  }
  
  def debug_(ref: ActorRef, log: String) = {
    EventHandler.debug(ref, log);
  }
  
  class LoggingActor extends Actor {
    def receive = {    
      case _ => null
    }
  }
}