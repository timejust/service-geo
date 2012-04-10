import sbt._
import Keys._
import com.github.siasia._
import PluginKeys._
import WebPlugin._

object ServiceGeoBuild extends Build {
  val Organization = "Timejust SA"
  val Version      = "1.0"
  val ScalaVersion = "2.9.1"  
  def Conf = config("container")
  def jettyPort = 9000
  
  lazy val ServiceGeo = Project(
    id = "service-geo",
    base = file("."),
    settings = defaultSettings ++ WebPlugin.webSettings ++ 
      Seq(
        port in Conf := jettyPort,
        // env in Compile := Some(file(".") / "conf" / "jetty" / "jetty-env.xml" asFile),
        libraryDependencies ++= Dependencies.serviceGeo
      )
  )
  
  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := Organization,
    version      := Version,
    scalaVersion := ScalaVersion,
    crossPaths   := false,
    organizationName := "Timejust SA",
    organizationHomepage := Some(url("http://www.timejust.com"))
  )
    
  lazy val defaultSettings = buildSettings ++ Seq(
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/repo/",
    resolvers += "google-api-services" at "http://mavenrepo.google-api-java-client.googlecode.com/hg",
    resolvers += "Sonatype Repository" at "https://oss.sonatype.org/content/repositories/releases/",
    resolvers += "Scala-tools Repository" at "http://scala-tools.org/repo-snapshots/",
    
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")    
  )
}

object Dependencies {
  import Dependency._
  
  val serviceGeo = Seq(
    akkaActor, akkaHttp, liftJson, jettyWepapp, jettyPlus, servletApi, 
    asyncHttpClient, mysqlConnector, c3p0, scalaTest
  )
}

object Dependency {
  // Versions
  object V {
    val Akka = "1.3.1"
    val Lift = "2.4"
    val Jetty = "8.1.0.RC5"
  }
  
  val akkaActor         = "se.scalablesolutions.akka" % "akka-actor"            % V.Akka
  val akkaHttp          = "se.scalablesolutions.akka" % "akka-http"             % V.Akka
  val liftJson          = "net.liftweb"              %% "lift-json"             % V.Lift
  val liftMapper        = "net.liftweb"              %% "lift-mapper"           % V.Lift
  val jettyWepapp       = "org.eclipse.jetty"         % "jetty-webapp"          % V.Jetty   % "container"
  val jettyPlus         = "org.eclipse.jetty"         % "jetty-plus"            % V.Jetty   % "container"
  val servletApi        = "javax.servlet"             % "javax.servlet-api"     % "3.0.1"   % "provided"
  val asyncHttpClient   = "com.ning"                  % "async-http-client"     % "1.7.2"
  val mysqlConnector    = "mysql"                     % "mysql-connector-java"  % "5.1.19"
  val c3p0              = "c3p0"                      % "c3p0"                  % "0.9.1.2"
  val googleOauth2      = "com.google.apis"           % "google-api-services-oauth2"    % "v2-rev3-1.5.0-beta"
  val googleCalendar    = "com.google.apis"           % "google-api-services-calendar"  % "v3-rev3-1.5.0-beta"
  val casbah            = "com.mongodb.casbah"       %% "casbah"                % "2.1.5-1"
  val scalaTest         = "org.scalatest"            %% "scalatest"             % "1.7.1"   % "test"
  
  // val akkaSlf4j         = "com.typesafe.akka"         % "akka-slf4j"         % V.Akka
  // val logback           = "ch.qos.logback"    % "logback-classic"    % "1.0.0" 
}