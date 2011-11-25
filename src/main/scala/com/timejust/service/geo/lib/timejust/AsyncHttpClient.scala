package com.timejust.service.geo.lib.timejust

import akka.actor._
import akka.event.EventHandler
import akka.routing.CyclicIterator
import akka.routing.Routing
import akka.routing.Routing.Broadcast
import com.ning.http.client._
import com.ning.http.client.AsyncHandler._

object AsyncHttpClientPool {
  class HttpRequest(ar0: String, ar1: String, ar2: Map[String, Iterable[String]]) {
    val id = ar0
    val url = ar1
    val params = ar2
  }
  class HttpResponse(ar0: String, ar1: Int, ar2: String, ar3: String, 
    ar4: Map[String, String]) {
    val id = ar0
    val code = ar1
    val text = ar2
    val content = ar3
    val headers = ar4
  }
  
  case class Get(id: String, url: String, params: Map[String, Iterable[String]])
  case class Gets(id: String, reqs: List[HttpRequest])  
  case class Complete(id: String, resps: List[HttpResponse])
  
  /**
   * Create the http client actors
   */
  val actors = Vector.fill(8)(Actor.actorOf[AsyncHttpActor].start())
  
  /**
   * Wrap them with a load-balancing router
   */
  val client = Routing.loadBalancerActor(CyclicIterator(actors)).start()
  
  /**
   * Aynchronous http client actor using ning's async http library
   */
  class AsyncHttpActor extends Actor {
    /**
     * Ning's async http client object
     */
    var client: AsyncHttpClient = null
    
    /**
     * Create http client before starting the actor
     */
    override def preStart() = { client = new AsyncHttpClient }
    
    /**
     * Convet scala data structure to java ones for addQueryParameter 
     * function ning's async http client.
     */
    def toQueryParameters(sMap: Map[String, Iterable[String]]) = {
      var jMap = new java.util.HashMap[String, java.util.Collection[String]]()
      sMap.foreach({x=>
        var array = new java.util.ArrayList[String]()
        x._2.foreach({y=>array.add(y)})
        jMap.put((x._1), array)
      })
      new FluentStringsMap(jMap)
    }

    def toHeaders(headers: FluentCaseInsensitiveStringsMap) = {
      /*
      for (var i: Iterator[Entry[String, List[String]]] = headers.iterator(); i.hasNext()) {
        var param: Map.Entry[String, List[String]] = i.next();
        var name = param.getKey();
        */
        /*
        for (Iterator[String] j = param.getValue().iterator(); j.hasNext(); ) {
                              String value = j.next();
                              if (encode) {
                                  UTF8UrlEncoder.appendEncoded(builder, name);
                              } else {
                                  builder.append(name);
                              }
                              if (value != null && !value.equals("")) {
                                  builder.append('=');
                                  if (encode) {
                                      UTF8UrlEncoder.appendEncoded(builder, value);
                                  } else {
                                      builder.append(value);
                                  }
                              }
                              if (j.hasNext()) {
                                  builder.append('&');
                              }
                          }
                          if (i.hasNext()) {
                              builder.append('&');
                          }*/
                     // }
    }
    
    def receive = {
      case Get(id, url, params) =>
        val f = client.prepareGet(url).setQueryParameters(toQueryParameters(params)).
          execute(new AsyncHandler[Response]() {            
          val builder = new Response.ResponseBuilder()
          
          def onThrowable(t: Throwable) {
            EventHandler.error(this, t.getMessage)
          }
    
          def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
            // builder.append(new String(bodyPart.getBodyPartBytes()))              
            builder.accumulate(bodyPart)
            STATE.CONTINUE
          }
    
          def onStatusReceived(status: HttpResponseStatus) = {
            builder.reset();
            builder.accumulate(status)
            STATE.CONTINUE
          }
          
          def onHeadersReceived(headers: HttpResponseHeaders) = {
            builder.accumulate(headers)
            STATE.CONTINUE
          }
    
          def onCompleted() = {
            // builder.toString()
            builder.build()
          }
        })
    
        val res = f.get()
        self reply Complete(id, List[HttpResponse](new HttpResponse(id, res.getStatusCode(), 
          res.getStatusText(), res.getResponseBody(), null)))          
          
      case Gets(id, reqs) => 
        var httpResps = List[HttpResponse]()    
        reqs.foreach({x=> 
          val f = client.prepareGet(x.url).setQueryParameters(toQueryParameters(x.params)).
            execute(new AsyncHandler[Response]() {            
            val builder = new Response.ResponseBuilder()

            def onThrowable(t: Throwable) {
              EventHandler.error(this, t.getMessage)
            }

            def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
              // builder.append(new String(bodyPart.getBodyPartBytes()))              
              builder.accumulate(bodyPart)
              STATE.CONTINUE
            }

            def onStatusReceived(status: HttpResponseStatus) = {
              builder.reset();
              builder.accumulate(status)
              STATE.CONTINUE
            }

            def onHeadersReceived(headers: HttpResponseHeaders) = {
              builder.accumulate(headers)
              STATE.CONTINUE
            }

            def onCompleted() = {
              // builder.toString()
              builder.build()
            }
          })
          
          val res = f.get()
          httpResps ::= new HttpResponse(x.id, res.getStatusCode(), 
            res.getStatusText(), res.getResponseBody(), null)
        })
        
        self reply Complete(id, httpResps)
    }        
  }
}