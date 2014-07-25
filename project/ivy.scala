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
import com.typesafe.sbt.license._

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
      targetDir: File,
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
    
    dumpDepGraph(targetDir, reports)
    
    val licenses = for {
      report <- reports
      license <- LicenseReport.getLicenses(report, configs = Seq.empty)
    } yield license
    
    // Create reverse lookup table for licenses by artifact...
    val grouped = LicenseReport.groupLicenses(licenses)
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
  def dumpDepGraph(targetDir: File, reports: Seq[ResolveReport]): Unit = withPrintableFile(new File(targetDir, "local-repo-deps.txt")) { println =>
    // Here we make an assumption...
    // THE FIRST MODULE is the one that we wanted, the rest are
    // the ones we pulled in...
    for((report, id) <- reports.zipWithIndex) {
      val modules = report.getModuleIds.asInstanceOf[java.util.List[ModuleId]].asScala
      val requested = modules.head
      val name = requested.getOrganisation + ":" + requested.getName
      println(name + " - requested")

      val messages = report.getAllProblemMessages().asInstanceOf[java.util.List[String]].asScala

      for (msg <- messages) {
        println("\t PROBLEM: " + msg)
      }

      val evicted = Option(report.getEvictedNodes()).map(_.toSeq).getOrElse(Nil)
      for (e <- evicted) {
        println("\t EVICTED: " + e)
      }

      val artifacts = Option(report.getAllArtifactsReports()).map(_.toSeq).getOrElse(Nil)
      for (a <- artifacts) {
        println("\t ARTIFACT: " + a)
      }

      // Now find what we got:
      val deps = for {
	    dep <- report.getDependencies.asInstanceOf[java.util.List[IvyNode]].asScala
	    if dep != null
	    depId = dep.getId
	    //if !((depId.getOrganisation == requested.getOrganisation) && (depId.getName == requested.getName))
	  } yield depId.getOrganisation + ":" + depId.getName + ":" + depId.getRevision
	  
	  deps foreach { dep => println("\t DEPENDENCY: " + dep) }
    }
  }
  
}
