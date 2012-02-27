package com.timejust.service.geo.lib.timejust

/**
 * MySQLConnectionPool.scala
 *
 * @author Min S. Kim (minsikzzang@gmail.com, minsik.kim@timejust.com)
 */ 

import akka.config.Config._ 
import com.mchange.v2.c3p0.ComboPooledDataSource

object MySQLConnectionPool {
  val host = config.getString("service-geo.geo-location-db.host", "localhost:3306")
  val db = config.getString("service-geo.geo-location-db.database", "geo_location")
  val username = config.getString("service-geo.geo-location-db.username", "root")
  val password = config.getString("service-geo.geo-location-db.password", "sa")  
  val encoding = config.getString("service-geo.geo-location-db.encoding", "utf8")  
  val connection = "jdbc:mysql://" + host + "/" + db + "?autoReconnect=true"
    
  var cpds = {
    var c = new ComboPooledDataSource
    c.setDriverClass("com.mysql.jdbc.Driver")
    c.setJdbcUrl(connection)
    c.setUser(username)
    c.setPassword(password)

    c.checkoutTimeout(10000)
    c.setMinPoolSize(1)
    c.setAcquireIncrement(1)
    c.setMaxPoolSize(50)   
    c 
  }
  
  def getConnection = {
    cpds.getConnection
  }  
}