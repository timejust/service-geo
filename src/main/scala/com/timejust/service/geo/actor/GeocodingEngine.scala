package com.timejust.service.geo.actor

import akka.actor._
import akka.config.Config._ 
import akka.config.Supervision.OneForOneStrategy
import akka.config.Supervision.Permanent
import akka.dispatch.Dispatchers
import akka.event.EventHandler
import akka.http.RequestMethod
import akka.routing.Routing.Broadcast
import akka.routing.CyclicIterator
import akka.routing.Routing
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._
import com.timejust.bootstrap.geo.Worker
import com.timejust.service.geo.lib.google._
import com.timejust.service.geo.lib.google.Geocoding._
import com.timejust.service.geo.lib.google.Places._
import com.timejust.service.geo.lib.timejust._
import java.net.URLDecoder
import java.net.URLEncoder
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._

/**
 * Timejust Geocoding proxy enine which uses semantic, 
 * google geocoding and place apis.
 */
object GeocodingEngine {
  /**
   * Geo request and response holder also support transform function
   * to map
   */
  case class GeoReq(id: String, geo: String, src: String)
  case class GeocodingInfo(address: String, lat: Double, lng: Double)
  
  class GeoRes(arg0: String, arg1: String, arg2: List[GeocodingInfo]) {    
    val id = arg0
    var status = arg1
    var res = arg2
    
    def toJObject = {
      (id -> (("status" -> status) ~ 
        ("results" -> res.map({x=>
          ("address" -> x.address) ~ ("location" -> 
            ("lat" -> x.lat) ~ ("lng" -> x.lng))}))))      
    }
  }

  class GeocodingApi(_id: String, _get: RequestMethod, _method: Int, _results: List[GeoRes]) {
    val id = _id
    val request = _get
    var method = _method
    var results = _results
  }
  
  sealed trait Message
  
  /**
   * Event signal from endpoint to worker actor
   */
  case class GeoRequest(geoList:List[GeoReq], get:RequestMethod) extends Message
     
  val googleApiKey = 
    config.getString("google.api-key", "AIzaSyCN7jqExZDKOnQYo01Vc2zNja9d_tSQeiQ")
   
  // Create the Geocoding actors
  val actors = Vector.fill(4)(Actor.actorOf[GeocodingHandler].start())

  // Wrap them with a load-balancing router
  val geocodingActor = Routing.loadBalancerActor(CyclicIterator(actors)).start()
  
  /**
   * Geocoding handler parses input request and run semantic engine to polish
   * out the given addresses, and pass to each suitable third party engine to
   * get appropriate address name.
   */
  class GeocodingHandler() extends Actor {   
    self.lifeCycle = Permanent  
    
    var geoResps = Map[String, GeocodingApi]()
    
    val reqNone = 0
    val reqGoogleGeocoding = 1
    val reqGooglePlace = 2
    
    def receive = {    
      // Handle get request
      case GeoRequest(geoList, get) =>
        val reqId = System.currentTimeMillis().toString()        
        var geoCodes = List[Geocode]()      
        var places = List[Place]()
        var geoPreResp = List[GeoRes]()
        var method = reqNone
        var geo = ""
        var status = ""
        
        geoList.foreach(x => {
          geo = x.geo
          status = "ok"
          
          // So some typographic cleansing the given geo value
          var result = SemanticAnalysisFR.typographicCleansing(geo)
          result = SemanticAnalysisFR.lexicalAnalysis(result)    
          val r = SemanticAnalysisFR.syntaxAnalysis(result)         
          
          if (r != SemanticAnalysisFR.syntax_ok) {
            // If there is syntax error, just return error without geo data
            status = r
            geo = ""
          } else {
            // If syntax is ok, we do some alaysis whether it contains
            // postal address, place names, or firms.
            var googleGeo = true
            var add = SemanticAnalysisFR.getPostalAddress(result) 
            if (add == null) {
              add = SemanticAnalysisFR.getPlaceName(result)
              if (add != null) {
                googleGeo = false
              }
            } 

            result = if (add != null) { add.trim } else { result.trim }

            val loc = GeoLocation.getLocation(x.src)
            var latlng = ""
            var country = "fr"
            
            if (loc != null) {
              latlng = loc.latitude + "," + loc.longitude
              country = loc.country
              // println(loc.city)
            }
                                
            if (googleGeo == true) {        
              method |= reqGoogleGeocoding  
              
              // Use google geocoding api to recognize the given address
              geoCodes ::= new Geocode(x.id, result, "",/*latlng,*/ 
                (loc.latitude.toFloat - 0.3).toString + "," + 
                (loc.longitude.toFloat - 0.3).toString + "|" +
                (loc.latitude.toFloat + 0.3).toString + "," + 
                (loc.longitude.toFloat + 0.3).toString, false, country)
            } else {
              method |= reqGooglePlace  
              
              // Use google places search api to recognize the given 
              // place information
              places ::= new Place(x.id, googleApiKey, latlng, "50000", result)
            }                            
          }      
                    
          geoPreResp ::= new GeoRes(
            x.id, status, List[GeocodingInfo](GeocodingInfo(geo, 0.0, 0.0)))    
        })      
                           
        if ((method & reqGoogleGeocoding) > 0 || (method & reqGooglePlace) > 0) {
          geoResps += reqId -> new GeocodingApi(reqId, get, method, geoPreResp)
        }
        
        if ((method & reqGoogleGeocoding) > 0) {
          Geocoding.geocoding ! Geocoding.Request(reqId, self, geoCodes)                
        }
          
        if ((method & reqGooglePlace) > 0) {
          Places.place ! Places.Request(reqId, self, places)
        }
        
        if (method == reqNone) {
          var api = new GeocodingApi(reqId, get, method, geoPreResp)
          var results = List[(String, JsonAST.JObject)]()
          api.results.foreach({x=>
            results ::= x.toJObject
          })
          
          val json = ("status" -> "ok") ~ ("results" -> results)
          get.OK(compact(JsonAST.render(json)))
        } 
        
      case Geocoding.Response(id, resps) =>      
        val geoApi: GeocodingApi = geoResps.get(id).orNull
        val geoRespList = geoApi.results           
        var results = List[(String, JsonAST.JObject)]()
        var status = "ok"
        
        if (geoApi == null || geoApi.request == null) {
          // No get object, something really bad happened.
          // just clear memories and do nothing.  
          geoResps -= id   
          EventHandler.error(self, "cannot find get object associated id - " + id)          
        } else if (geoRespList == null) {
          status = "internal error"
        } else {               
          var gcRes: GeocodeResult = null          
          geoRespList.foreach({x=>
            gcRes = resps.get(x.id).orNull
            if (gcRes != null) {
              x.status = if (gcRes.success) { "ok" } else { "google geocode error" }
              x.res = gcRes.results        
            } 
            results ::= x.toJObject
          })         
        }
                     
        if (geoApi != null) {
          geoApi.method -= reqGoogleGeocoding                         
          if (geoApi.request != null && geoApi.method == 0) {
            val json = ("status" -> status) ~ ("results" -> results)
            geoApi.request.OK(compact(JsonAST.render(json)))
            geoResps -= id   
          }          
        }   
        
      case Places.Response(id, resps) =>   
        val geoApi: GeocodingApi = geoResps.get(id).orNull
        val geoRespList = geoApi.results           
        var results = List[(String, JsonAST.JObject)]()
        var status = "ok"
        
        if (geoApi == null || geoApi.request == null) {
          // No get object, something really bad happened.
          // just clear memories and do nothing.  
          geoResps -= id  
          EventHandler.error(self, "cannot find get object associated id - " + id)           
        } else if (geoRespList == null) {
          status = "internal error"
        } else {                    
          var gcRes: PlaceResult = null          
          geoRespList.foreach({x=>
            gcRes = resps.get(x.id).orNull
            if (gcRes != null) {
              x.status = if (gcRes.success) { "ok" } else { "google place error" }
              x.res = gcRes.results
            } 
            results ::= x.toJObject
          })         
        }
              
        if (geoApi != null) {
          geoApi.method -= reqGooglePlace          
          if (geoApi.request != null && geoApi.method == 0) {
            val json = ("status" -> status) ~ ("results" -> results)          
            geoApi.request.OK(compact(JsonAST.render(json)))
            geoResps -= id   
          }                          
        }                    
    }  
  }  
}