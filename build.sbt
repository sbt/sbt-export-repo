lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      version := "0.1.0",
      organization := "com.eed3si9n"
    )),
    sbtPlugin := true,
    name := "sbt-export-repo",
    description := "sbt plugin to create to export repository",
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalacOptions := Seq("-deprecation", "-unchecked")
  )
