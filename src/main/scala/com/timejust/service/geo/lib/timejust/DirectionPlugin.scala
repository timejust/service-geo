package com.timejust.service.geo.lib.timejust

import akka.actor._
import com.timejust.service.geo.lib.timejust.Plugin._
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._

object DirectionPlugin {
  // Schedule class contains time and name of schedule and latitude and 
  // logitude of place where schedule happens
  case class Schedule(time: String, name: String, lat: Double, long: Double)
  
  case class Trip(departure: Schedule, arrival: Schedule, steps: List[Step]) {
    def toJObject = {
      ("trip") -> 
        ("dep_time" -> departure.time) ~ ("dep_lon" -> departure.long) ~ 
        ("dep_lat" -> departure.lat) ~ ("dep_name" -> departure.name) ~ 
        ("arr_time" -> arrival.time) ~ 
        ("arr_lon" -> arrival.long) ~ ("arr_lat" -> arrival.lat) ~ 
        ("arr_name" -> arrival.name) ~ 
        ("steps" -> steps.reverse.map({x=>x.toJObject}))
    }
  }
  
  class LocalTrip(departure: Schedule, arrival: Schedule, summary: String,
    steps: List[Step]) extends Trip(departure, arrival, steps) {
    val summary_ = summary
    override def toJObject = {
      ("trip") -> 
        ("dep_time" -> departure.time) ~ ("dep_lon" -> departure.long) ~ 
        ("dep_lat" -> departure.lat) ~ ("dep_name" -> departure.name) ~ 
        ("arr_time" -> arrival.time) ~ 
        ("arr_lon" -> arrival.long) ~ ("arr_lat" -> arrival.lat) ~ 
        ("arr_name" -> arrival.name) ~ ("summary" -> summary_) ~
        ("steps" -> steps.reverse.map({x=>x.toJObject}))
    }      
  }
  
  case class Step(departure: Schedule, arrival: Schedule, mean: String) {
    def toJObject = {
      ("mean" -> mean) ~ ("dep_time" -> departure.time) ~ 
      ("dep_lon" -> departure.long) ~ ("dep_lat" -> departure.lat) ~ 
      ("dep_name" -> departure.name) ~ 
      ("arr_time" -> arrival.time) ~ ("arr_lon" -> arrival.long) ~ 
      ("arr_lat" -> arrival.lat) ~ ("arr_name" -> arrival.name)      
    }
  }
  
  class LocalSteps(departure: Schedule, arrival: Schedule, mean: String,
    directions: List[Direction]) extends Step(departure, arrival, mean) {
    val directions_ = directions 
    override def toJObject = {
      ("mean" -> mean) ~ ("dep_time" -> departure.time) ~ 
      ("dep_lon" -> departure.long) ~ ("dep_lat" -> departure.lat) ~ 
      ("dep_name" -> departure.name) ~ 
      ("arr_time" -> arrival.time) ~ ("arr_lon" -> arrival.long) ~ 
      ("arr_lat" -> arrival.lat) ~ ("arr_name" -> arrival.name)
      ("directions" -> directions_.reverse.map({x=>x.toJObject}))     
    }  
  }  
  
  case class Direction(departure: Schedule, arrival: Schedule, mean: String,
    line: String, headsign: String, network: String, distance: Int, 
    duration: Int, text_direction: String) {
    def toJObject = {
      ("line" -> line) ~ ("headsign" -> headsign) ~ 
      ("network" -> network) ~ ("dep_time" -> departure.time) ~ 
      ("dep_lon" -> departure.long) ~ ("dep_lat" -> departure.lat) ~ 
      ("dep_name" -> departure.name) ~ ("arr_time" -> arrival.time) ~ 
      ("arr_lon" -> arrival.long) ~ ("arr_lat" -> arrival.lat) ~ 
      ("arr_name" -> arrival.name) ~ ("distance" -> distance) ~ 
      ("duration" -> duration) ~ ("text_direction" -> text_direction)
    }
  }
  
  case class Travel(trip: LocalTrip, format: String) {
    def toJObject: JsonAST.JObject = {
      (trip.toJObject)
    }
  }
  
  val modeDriving = "car"
  val modeTrain = "train"
  val modeBus = "bus"  
  val baseDeparture = "departure"
  val baseArrival = "arrival"
  
  abstract class DirPluggableActor extends PluggableActor {  
    def getModeTrain(): String = { modeTrain }
    def getModeBus(): String = { modeBus }
    def getModeDriving(): String = { modeDriving }
    def getModeDefault(): String = { modeTrain }
    def getDeparture(): String = { baseDeparture }
    def getArrival(): String = { baseArrival }
    def getBaseDefault(): String = { baseDeparture }

    def getBase(base: String): String = {
      if (base == baseArrival) { getArrival() }
      else if (base == baseDeparture) { getDeparture() }
      else { getBaseDefault() }
    }
    
    def getDirMode(mode: String): String = {
      val modes = mode.split(",")
      var str = ""
      var i = 0
      
      modes.map({m=>
        if (m == modeTrain) { str += getModeTrain() }
        else if (m == modeBus) { str += getModeBus() }
        else if (m == modeDriving) { str += getModeDriving() }
        else { str += getModeDefault() }
        if (i < modes.length - 1) {
          str += ","
        }
        i += 1
      })
      
      str
    }
  }
}
