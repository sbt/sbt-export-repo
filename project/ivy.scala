import sbt._
import org.apache.ivy.core.resolve.IvyNode
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.install.InstallOptions
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.util.filter.FilterHelper
import org.apache.ivy.core.resolve.IvyNode
import collection.JavaConverters._
import java.io.BufferedWriter
import org.apache.ivy.core.module.id.ModuleId


case class License(name: String, url: String)(val deps: Seq[String]) {
  override def toString = name + " @ " + url
}

object IvyHelper {
  

  
  /** Resolves a set of modules from an SBT configured ivy and pushes them into
   * the given repository (by name).
   * 
   * Intended usage, requires the named resolve to exist, and be on that accepts installed artifacts (i.e. file://)
   */
  def createLocalRepository(
      modules: Seq[ModuleID],
      localRepoName: String,
      ivy: IvySbt, 
      log: Logger): Seq[License] = ivy.withIvy(log) { ivy =>



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
    val reports = (modules flatMap installModule).toSeq
    
    dumpDepGraph(reports)
    
    val licenses = for {
      report <- reports
      dep <- report.getDependencies.asInstanceOf[java.util.List[IvyNode]].asScala
      if dep != null
      desc <- Option(dep.getDescriptor).toSeq
      license <- Option(desc.getLicenses) getOrElse Array.empty
    } yield License(license.getName, license.getUrl)(dep.getAllArtifacts.map(_.getName))
    
    // Create reverse lookup table for licenses by artifact...
    val grouped = for {
      (name, licenses) <- licenses.groupBy(_.name)
      l <- licenses.headOption.toSeq
    } yield License(l.name, l.url)(licenses flatMap (_.deps) distinct)
    
    grouped.toIndexedSeq
  }
  
  def withPrintableFile(file: File)(f: (Any => Unit) => Unit): Unit = {
    IO.createDirectory(file.getParentFile)
    Using.fileWriter(java.nio.charset.Charset.defaultCharset, false)(file) { writer =>
      def println(msg: Any): Unit = {
        System.out.println(msg)
        writer.write(msg.toString)
        writer.newLine()
      }
      f(println _)
    }
  }
  
    // TODO - Clean this up and put it somewhere useful.
  def dumpDepGraph(reports: Seq[ResolveReport]): Unit = withPrintableFile(new File("target/local-repo-deps.txt")) { println =>
    // Here we make an assumption...
    // THE FIRST MODULE is the one that we wanted, the rest are
    // the ones we pulled in...
    for((report, id) <- reports.zipWithIndex) {
      val modules = report.getModuleIds.asInstanceOf[java.util.List[ModuleId]].asScala
      val requested = modules.head
      val name = requested.getOrganisation + ":" + requested.getName
      println(name + " - requested")
      // Now find what we got:
      val deps = for {
	    dep <- report.getDependencies.asInstanceOf[java.util.List[IvyNode]].asScala
	    if dep != null
	    depId = dep.getId
	    if !((depId.getOrganisation == requested.getOrganisation) && (depId.getName == requested.getName))
	  } yield depId.getOrganisation + ":" + depId.getName + ":" + depId.getRevision
	  
	  deps foreach { dep => println("\t " + dep) }
    }
  }
  
}