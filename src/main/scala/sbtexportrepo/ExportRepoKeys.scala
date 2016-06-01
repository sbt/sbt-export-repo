package sbtexportrepo

import sbt._

trait ExportRepoKeys {
  lazy val exportRepo = taskKey[File]("Export repository")
  lazy val exportRepoName = settingKey[String]("""The name of the export repo ("preloaded-local")""")
  lazy val exportRepoDirectory = settingKey[File]("The directory for the export repo")
  lazy val exportRepoActualDirectory = settingKey[File]("The directory used during exporting")
  lazy val exportRepoTo = settingKey[Option[Resolver]]("The resolver to export to")
  lazy val exportRepoDeleteExisting = settingKey[Boolean]("Delete the existing content from the export repo")
}
