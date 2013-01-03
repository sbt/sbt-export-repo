import sbt._


object IvyHelper {
  
  /** Resolves a set of modules from an SBT configured ivy and pushes them into
   * the given repository (by name).
   * 
   * Intended usage, requires the named resovle to exist, and be on that accepts installed artifacts (i.e. file://)
   */
  def createLocalRepository(
      modules: Seq[ModuleID],
      localRepoName: String,
      ivy: IvySbt, 
      log: Logger): Unit = ivy.withIvy(log) { ivy =>

    import org.apache.ivy.core.module.id.ModuleRevisionId
    import org.apache.ivy.core.report.ResolveReport
    import org.apache.ivy.core.install.InstallOptions
    import org.apache.ivy.plugins.matcher.PatternMatcher
    import org.apache.ivy.util.filter.FilterHelper


    // This helper method installs a particular module and transitive dependencies.
    def installModule(module: ModuleID): Option[ResolveReport] = {
      // TODO - Use SBT's default ModuleID -> ModuleRevisionId
      val mrid = IvySbtCheater toID module
      val name = ivy.getResolveEngine.getSettings.getResolverName(mrid)
      log.debug("Module: " + mrid + " should use resolver: " + name)
      try Some(ivy.install(mrid, name, localRepoName,
                new InstallOptions()
                    .setTransitive(true)
                    .setValidate(true)
                    .setOverwrite(true)
                    .setMatcherName(PatternMatcher.EXACT)
                    .setArtifactFilter(FilterHelper.NO_FILTER)
                ))
       catch {
         case e: Exception =>
           log.debug("Failed to resolve module: " + module)
           log.trace(e)
           None
       }
    }
    // Grab all Artifacts
    modules foreach installModule     
  }
}