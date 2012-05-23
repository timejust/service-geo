package com.timejust.util

object Encoding {
  def encode(in: String, charset: String) = {
    new String(in.getBytes("ISO-8859-1"), charset) 
  }
  def encode(in: String, charset: String, destCharset: String) = {
    new String(in.getBytes(charset), destCharset) 
  }
}