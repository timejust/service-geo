import sbt._

class ServiceProject(info: ProjectInfo) extends DefaultWebProject(info) with AkkaProject {
  val AkkaRepo = "Akka Maven Repository" at "http://akka.io/repository"
  
  val akka_http = akkaModule("http")
  
  // val servletApi = "javax.servlet" % "servlet-api" % "2.5" % "provided"  
  lazy val JavaNet1 = "Java.net Maven1 Repository" at "http://download.java.net/maven/1/"  
  lazy val JavaNet = "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"  
  lazy val MapFishRepo = "MapFish Repository" at "http://dev.mapfish.org/maven/repository/"
  lazy val TypesafeRepo = "Typesafe Repository" at " http://repo.typesafe.com/typesafe/releases"
  
  override def libraryDependencies = Set(
    "org.eclipse.jetty" % "jetty-webapp" % "8.0.0.RC0" % "provided",
    // "org.eclipse.jetty" % "jetty-servlet" % "8.0.0.RC0" % "provided",
    "org.mortbay.jetty" % "servlet-api" % "3.0.20100224" % "provided",
    "net.liftweb" % "lift-json_2.9.1" % "2.4-M5" % "compile",
    "ch.qos.logback" % "logback-classic" % "0.9.28" % "runtime",
    "com.ning" % "async-http-client" % "1.6.4" % "compile"
  ) ++ super.libraryDependencies        
  
  override def jettyPort = 9000
   
  override def ivyXML =
    <dependencies>
      <dependency org="se.scalablesolutions.akka" name="akka-http" rev="1.3-RC1">
        <exclude module="jetty"/>
      </dependency>
    </dependencies>
}
