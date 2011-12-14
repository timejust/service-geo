package com.timejust.service.geo.lib.timejust

import akka.actor._
import com.timejust.service.geo.lib.timejust.Plugin._
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._

object DirectionPlugin {
  // Schedule class contains time and name of schedule and latitude and 
  // logitude of place where schedule happens
  case class Schedule(time: String, name: String, lat: Double, long: Double)
  
  case class Direction(departure: Schedule, arrival: Schedule, mean: String, 
    line: String, headsign: String, network: String, milestones: String,
    walk: String, distance: Int, duration: Int, text_direction: String,
    schedule: String, availability: String, booking_number: String) {
    def toJObject = {
      ("mean" -> mean) ~ ("line" -> line) ~ ("headsign" -> headsign) ~ 
      ("network" -> network) ~ ("departure_time" -> departure.time) ~ 
      ("departure_lon" -> departure.long) ~ ("departure_lat" -> departure.lat) ~ 
      ("departure_name" -> departure.name) ~ ("arrival_time" -> arrival.time) ~ 
      ("arrival_lon" -> arrival.long) ~ ("arrival_lat" -> arrival.lat) ~ 
      ("arrival_name" -> arrival.name) ~ ("milestones" -> milestones) ~ 
      ("walk" -> walk) ~ ("distance" -> distance) ~ 
      ("duration" -> duration) ~ ("text_direction" -> text_direction) ~ 
      ("schedule" -> schedule) ~ ("availability" -> availability) ~ 
      ("booking_number" -> booking_number)        
    }
  }
  
  // Subset is a collection of directions by transporation mean
  case class TripSubset(departure: Schedule, arrival: Schedule, duration: Int,
    directions: List[Direction]) {
    def toJObject = {
      ("departure_time" -> departure.time) ~ ("departure_lon" -> departure.long) ~ 
      ("departure_lat" -> departure.lat) ~ ("departure_name" -> departure.name) ~ 
      ("duration" -> duration) ~ ("arrival_time" -> arrival.time) ~ 
      ("arrival_lon" -> arrival.long) ~ ("arrival_lat" -> arrival.lat) ~ 
      ("arrival_name" -> arrival.name) ~ ("directions" -> directions.map({x=>x.toJObject}))
    }
  }
  
  // Set is also a collection of directions but it is bigger element for UI
  case class TripSet(departure: Schedule, arrival: Schedule, duration: Int,
    subsets: List[TripSubset]) {
    def toJObject = {
      ("departure_time" -> departure.time) ~ ("departure_lon" -> departure.long) ~ 
      ("departure_lat" -> departure.lat) ~ ("departure_name" -> departure.name) ~ 
      ("duration" -> duration) ~ ("arrival_time" -> arrival.time) ~ 
      ("arrival_lon" -> arrival.long) ~ ("arrival_lat" -> arrival.lat) ~ 
      ("arrival_name" -> arrival.name) ~ ("subsets" -> subsets.map({x=>x.toJObject}))
    }
  }
    
  case class Trip(departure: Schedule, arrival: Schedule, duration: Int,
    summary: String, sets: List[TripSet]) {
    def toJObject = {
    ("trip") -> 
      ("departure_time" -> departure.time) ~ ("departure_lon" -> departure.long) ~ 
      ("departure_lat" -> departure.lat) ~ ("departure_name" -> departure.name) ~ 
      ("duration" -> duration) ~ ("arrival_time" -> arrival.time) ~ 
      ("arrival_lon" -> arrival.long) ~ ("arrival_lat" -> arrival.lat) ~ 
      ("arrival_name" -> arrival.name) ~ ("summary" -> summary) ~ 
      ("sets" -> sets.map({x=>x.toJObject}))
    }
  }
    
  case class Travel(trip: List[Trip]) {
    def toJObject: JsonAST.JObject = {
      ("travel" -> trip.map{x=>x.toJObject})
    }
  }
  
  val modeDriving = "driving"
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
      if (mode == modeTrain) { getModeTrain() }
      else if (mode == modeBus) { getModeBus() }
      else if (mode == modeDriving) { getModeDriving() }
      else { getModeDefault() }
    }
  }
}
