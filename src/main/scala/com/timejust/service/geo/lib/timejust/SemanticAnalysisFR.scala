package com.timejust.service.geo.lib.timejust

import scala.util.matching.Regex

/**
 * @brief
 */
object SemanticAnalysisFR {
  val syntax_ok = "syntax_ok"
  val syntax_call = "syntax_error_call"
  val syntax_tbd = "sytax_error_tbd"

  object PlaceType {
    val hotel = "lodging"
    val train_station = "train_station"
    val metro_station = "subway_station"
    val hospital = "hospital"
  }
  
  case class Place(name: String, types: List[String])
  
  def typographicCleansing(geo: String): String = {
    var result = geo
    // Typographic cleansing    
    val cleansing = """^[ \t]*([^ ].*)$""".r
    geo match {
      case cleansing(res0) => result = res0
      case _ => 
    }
    result
  }
  
  def lexicalAnalysis(geo: String): String = {
    var result = geo
    
    // Combined lexical and syntax analysis and extraction      
    // LEXEMES SEQUENCE = DIRECTION (IDENTIFICATION + DELETION)
    // Token = CS
    val cs = """^(.*)([Cc][Ss][ \t]?\d+)(\D.*)$""".r
    result match {
      case cs(res0, res1, res2) => result = res0 + res2
      case _ => 
    }

    // Token = Code
    val code = """^(.*\d.*)([Cc][oO][Dd][Ee].*$)""".r
    result match {
      case code(res0, res1) => result = res0
      case _ => 
    }
    
    val cedex_ = """^(.*)([Cc][oO][Dd][Ee][ \t]?\w?\d+\w*\d+)(\W.*$|$)""".r
    result match {
      case cedex_(res0, res1, res2) => result = res0 + res2
      case _ => 
    }
    
    val cedex = """^(.*)([Cc][eE][Dd][Ee][Xx][ \t]?\d+)(.*)$""".r
    result match {
      case cedex(res0, res1, res2) => result = res0 + res2
      case _ => 
    }
    
    // Token = Interphone
    val interphone = """^(.*\d.*)([Ii]nterphone.*)$""".r
    result match {
      case interphone(res0, res1) => result = res0
      case _ => 
    }
    
    // Token = Dans
    val dans = """^(.{5,})[Dd]ans (.*)$""".r
    result match {
      case dans(res0, res1) => result = res0
      case _ => 
    }
    
    // Token = Etage
    val etage_ = """^(.*)(?:^|[ \t])\d\w*[ \t]?(?:e|é|E)tage[ \t]?(.*)$""".r
    result match {
      case etage_(res0, res1) => result = res0 + " " + res1
      case _ => 
    }
    
    val etage = """^(.*)(?:^|[ \t])[ \t]?(?:e|é|E)tage[ \t]?\d(.*)$""".r
    result match {
      case etage(res0, res1) => result = res0 + " " + res1
      case _ => 
    }
    
    // Token = URL
    val url = """^(.*)[ \t]?http[^ ]*[ \t]?(.*)$""".r
    result match {
      case url(res0, res1) => result = res0 + " " + res1
      case _ => 
    }
    
    // LEXEMES SEQUENCE = PLACE NAMES (IDENTIFICATION + EXTRACTION)
    // Token = Chez    
    val chez = """^(.{2,})[Cc]hez [^ ]{4,}[ \t]*[^ ]*[ \t]?$""".r
    result match {
      case chez(res0) => result = res0
      case _ => 
    }
    
    val acote_ = """^(.{10,})(?:A|a|à)[ \t]?[Cc](?:o|ô)t(?:é|e).{0,15}$""".r
    result match {
      case acote_(res0) => result = res0
      case _ => 
    }
    
    // if (result == geo) { "" } else { result }
    result
  }
  
  def syntaxAnalysis(geo: String): String = {
    var result = syntax_ok
    
    // LEXEMES SEQUENCE = CALLS (DETECTION)
    // Token = CALL
    if (geo.matches("""^(.*)[Cc]all(.*)$""") || geo.matches("""^(.*)[Tt]eléphon(.*)$""") || 
      geo.matches("""^(.*)[Tt]elephon(.*)$""") || geo.matches("""^(.*)[Tt]élephon(.*)$""") || 
      geo.matches("""^(.*)[Tt]éléphon(.*)$""") || geo.matches("""^(.*)[Tt]élephon(.*)$""") || 
      geo.matches("""^(.*)[Ss]kype(.*)$""") || geo.matches("""^(.*)FT(.*)$""") || 
      geo.matches("""^(.*)[Cc][Cc][mM](.*)$""")) {
      result = syntax_call
    } else if (geo.matches("""/.*([Aa]|à)[ \t]?[Dd](e|é)finir.*/""") || 
      geo.matches("""/.*[Tt][Bb][Dd].*/""")) {
      result = syntax_tbd
    }
    
    result
  }
  
  def getPostalAddress(geo: String): String = {
    var addr: String = null
    var regex0 = """^(?:(?:\d{1,4}(?:[ac-st-zAC-ST-Z\/;.\-]|é|è|(?:[ \t,](?=\d))))|\D)*(([Pp]lace|[Rr]ue|[Bb]oulevard|[Bb]l?d|[Aa]llee|[Aa]venue).*)$""".r
    var regex1 = """^(?:(?:\d{1,4}(?:[ac-st-zAC-ST-Z\/;.\-]|é|è|(?:[ \t,](?=\d))))|\D)*(?:\W*)(\d{1,4}\D{2,}\d{2}[ \t]?\d{3}([ \t]?[^ ]*)).*$""".r
    var regex2 = """^(?:(?:\d{1,4}(?:[ac-st-zAC-ST-Z\/;.\-]|é|è|(?:[ \t,](?=\d))))|\D)*(?:\W*)(\d{1,4}\D+).*$""".r
    var regex3 = """^.*(([Pp]lace|[Rr]ue|[Bb]oulevard|[Bb]l?d|[Aa]llee|[Aa]venue).*)$""".r
    
    geo match {
      case regex0(res0) => addr = res0
      case regex1(res0) => addr = res0
      case regex2(res0) => addr = res0
      case regex3(res0) => addr = res0
      case _ => addr = null
    }
    
    addr
  }
  
  def getPlaceName(geo: String): String = {
    var place: String = null
    val acote = """^(?:A|a|à)[ \t]?[Cc](?:o|ô)t(?:é|e)[ \t]?d[^ \']*\'?(.*)$""".r
    val hospital = """^\D*(([Hh]opital|[Cc]lini[(que)c]).{9}\w*)(.*)$""".r
    val station = """^.*(([Gg]are|[Ss]tation|[Mm]etro|[Mm]°).{6}\w*)(.*)$""".r
    
    val hotel = """^.*(([Hh][oô]tel)\w*)(.*)$""".r
    
    val aprox = """^(?:A|a|à)[ \t]?[Pp]roximit(?:é|e)[ \t]?d[^ \']*\'?(.*)$""".r
    val chez_ = """^[Cc]hez[ \t]?[ \t]?([^ ]*)[ \t]?$""".r
    
    geo match {
      case acote(res0) => place = res0
      case hospital(res0, res1, res2) => place = res0
      case station(res0, res1, res2) => place = res0
      case aprox(res0) => place = res0
      case chez_(res0) => place = res0
      case _ => 
    }
    
    place
  }
}