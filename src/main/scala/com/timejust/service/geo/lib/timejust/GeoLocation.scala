package com.timejust.service.geo.lib.timejust

import akka.config.Config._ 
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

object GeoLocation {
  case class Location(country: String, city: String, latitude: String, longitude: String)
  
  // TODO
  // Implement connection pool instead of using direct connection. 
  // Even use caching for ip location info
  val host = config.getString("service-geo.geo-location-db.host", "localhost:3306")
  val db = config.getString("service-geo.geo-location-db.database", "geo_location")
  val username = config.getString("service-geo.geo-location-db.username", "root")
  val password = config.getString("service-geo.geo-location-db.password", "sa")  
  val encoding = config.getString("service-geo.geo-location-db.encoding", "utf8")  
  val connection = "jdbc:mysql://" + host + "/" + db + "?user=" + username + "&password=" + 
    password + "&characterEncoding=" + encoding
  
  // Load the driver
  val driver = classOf[com.mysql.jdbc.Driver]
  
  def ipToNumber(ip: String) = {
    val tokens = ip.split("[.]+")
    (tokens(0).toInt * 16777216) + (tokens(1).toInt * 65536) +
      (tokens(2).toInt * 256) + tokens(3).toInt
  }
  
  def getLocation(ip: String) = {
    // Setup the connection
    val conn = DriverManager.getConnection(connection)
    var location: Location = null
    
    try {
      // Configure to be Read Only
      val statement = conn.createStatement(
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      
      val numIp = ipToNumber(ip)
      
      // Execute query
      val rs = statement.executeQuery("select a.location_id, b.country, b.city, b.latitude, " + 
        "b.longitude from geo_ip_city as a join geo_city_location as b " + 
        "on a.location_id = b.location_id where a.begin_ip <= " + numIp + 
        " and a.end_ip >= " + numIp)

      // We only read first element
      if (rs.next) {
        location = Location(rs.getString("b.country"), 
          rs.getString("b.city"), rs.getString("b.latitude"), rs.getString("b.longitude"))
      }
    } finally {
      conn.close()
    }
    
    location
  }
}