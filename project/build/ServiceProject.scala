import sbt._

class ServiceProject(info: ProjectInfo) extends DefaultWebProject(info) with AkkaProject {
  val AkkaRepo = "Akka Maven Repository" at "http://akka.io/repository"
  
  val akka_http = akkaModule("http")
  
  // val servletApi = "javax.servlet" % "servlet-api" % "2.5" % "provided"
  // lazy val JavaNet = "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"
  
  override def libraryDependencies = Set(
      "org.eclipse.jetty" % "jetty-webapp" % "8.0.1.v20110908" % "jetty",
      "org.eclipse.jetty" % "jetty-servlet" % "8.0.1.v20110908" % "provided"
  ) ++ super.libraryDependencies
      
  override def ivyXML =
    <dependencies>
      <dependency org="se.scalablesolutions.akka" name="akka-http" rev="1.3-RC1">
        <exclude module="jetty"/>
      </dependency>
    </dependencies>
}
