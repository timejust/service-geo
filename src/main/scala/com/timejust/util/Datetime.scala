package com.timejust.util

import java.util.Calendar
import java.text.SimpleDateFormat

object Datetime {
  def unixToDateString(unix: String) = {
    val c = Calendar.getInstance()
    if (unix == null) {
      c.setTimeInMillis(System.currentTimeMillis())  
    } else {
      // If the given unix time is null, get current time stamp
      c.setTimeInMillis(unix.toLong * 1000)  
    }          

    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    sdf.format(c.getTime())      
  }  
  
  def unixToDateString(unix: Long) = {
    val c = Calendar.getInstance()
    if (unix == 0) {
      c.setTimeInMillis(System.currentTimeMillis())  
    } else {
      // If the given unix time is null, get current time stamp
      c.setTimeInMillis(unix * 1000)  
    }          

    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    sdf.format(c.getTime())      
  }  
}
