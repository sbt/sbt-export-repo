package sbtexportrepo

import sbt._, Keys._

object ExportRepoPlugin extends AutoPlugin {
  def required = plugins.JvmPlugin

  lazy val ExportRepoConfig = config("exportrepo")

  object autoImport extends ExportRepoKeys
  import autoImport._
  override def globalSettings: Seq[Def.Setting[_]] =
    Seq(
      exportRepoDeleteExisting := false,
      exportRepoName := "preloaded-local"
    )
  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      exportRepo := {
        val st = streams.value
        val projDeps = (projectDependencies in exportRepo).value
        val libraryDeps = (libraryDependencies in exportRepo).value
        val tmp = exportRepoActualDirectory.value
        val out = exportRepoDirectory.value
        // val ms = exportRepoModules.value
        val repoName = exportRepoName.value
        val del = exportRepoDeleteExisting.value
        update.value
        ExportRepo.install(projDeps, libraryDeps,
          Some(scalaVersion.value), ivyConfigurations.value,
          repoName, tmp, out, target.value, del,
          (ivySbt in exportRepo).value, st.log)
        out
      },
      exportRepoDirectory := { target.value / exportRepoName.value },
      // This is used for actual Ivy installation.
      // However, due to the way Ivy works, we need to add this to the resolver list.
      exportRepoActualDirectory := { target.value / (exportRepoName.value + "-temp") },
      exportRepoTo := {
        val repo = Resolver.file(exportRepoName.value, exportRepoActualDirectory.value)(Resolver.ivyStylePatterns)
        Some(repo)
      },
      resolvers ++= exportRepoTo.value.toList,
      // fullResolvers := {
      //   val old = fullResolvers.value
      //   old filter { _.name != "inter-project" }
      // },
      ivySbt in exportRepo := ivySbt.value,
      projectDependencies in exportRepo := projectDependencies.value,
      libraryDependencies in exportRepo := libraryDependencies.value
    )
}
