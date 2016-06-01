lazy val root = (project in file(".")).
  aggregate(app, dist).
  settings(
    inThisBuild(List(
      organization := "com.example.exportrepo",
      scalaVersion := "2.11.8",
      version := "0.1.0"
    )),
    commonSettings,
    name := "hello-root",
    publish := (),
    publishLocal := ()
  )

lazy val commonSettings = Seq(
  ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache"))
  )

lazy val app = (project in file("app")).
  settings(
    commonSettings,
    name := "hello",
    libraryDependencies += "commons-io" % "commons-io" % "1.3"
  )

lazy val dist = (project in file("dist")).
  enablePlugins(ExportRepoPlugin).
  dependsOn(app).
  settings(
    commonSettings,
    name := "dist",
    libraryDependencies += "org.typelevel" %% "cats" % "0.6.0",
    publish := (),
    publishLocal := ()
  )
