package com.timejust.service.geo.lib

import com.timejust.service.geo.lib.timejust.SemanticAnalysisFR
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import scala.util.Random
import scala.sys.process._
import scala.util.parsing.json.JSON

class SemanticTest extends FunSuite with BeforeAndAfter {  
  before {         
  }

  after {
  }
    
  def doSemantic(input: String) = {
    println("original address: " + input)
    var result = SemanticAnalysisFR.typographicCleansing(input)
    println("after typographicCleansing: " + result)
    result = SemanticAnalysisFR.lexicalAnalysis(result)    
    println("after lexicalAnalysis: " + result)
    val r = SemanticAnalysisFR.syntaxAnalysis(result)         
    println("after syntaxAnalysis: " + r)
    
    assert(r == SemanticAnalysisFR.syntax_ok)
      
    // If syntax is ok, we do some alaysis whether it contains
    // postal address, place names, or firms.
    var googleGeo = true
    var add = SemanticAnalysisFR.getPostalAddress(result) 
    println("after getPostalAddress: " + add)
    add = SemanticAnalysisFR.getPlaceName(result)
    println("after getPlaceName: " + add)
    add
  }
  
  test("semantic process") {
    doSemantic("2 Rue Richard Lenoir 75011 Paris")
    doSemantic("209 E 19th St Manhattan, NY 10003")
        
  }
}
