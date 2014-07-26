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

    ridiculousHacks(new File(targetDir, "local-repository"), log)

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

  def ridiculousHacks(dir: File, log: Logger): Unit = {
    if (!dir.exists)
      sys.error(s"$dir doesn't exist")

    val jsch38jar = new File(dir, "com.jcraft/jsch/0.1.38/jars/jsch.jar")
    val jsch38ivy = new File(dir, "com.jcraft/jsch/0.1.38/ivys/ivy.xml")
    if (jsch38jar.exists) {
      log.warn(s"$jsch38jar already exists, might be able to remove this hack (try clean;offlineTests without the hack first though)")
    } else if (jsch38ivy.exists) {
      log.warn("Broken local repo didn't get jsch 0.1.38, fixing it via ridiculous hack")
      jsch38jar.getParentFile.mkdirs()
      IO.download(new URL("http://central.maven.org/maven2/com/jcraft/jsch/0.1.38/jsch-0.1.38.jar"),
                  jsch38jar)
      if (!jsch38jar.exists)
        sys.error(s"Failed to download $jsch38jar")
    } else {
      log.warn(s"$jsch38ivy doesn't exist so this hack can probably be removed (try clean;offlineTests without the hack first though)")
    }

    def replaceInFile(file: java.io.File, ifText: String, oldText: String, newText: String, msg: String): Boolean = {
      if (!file.exists())
        throw new RuntimeException(s"no such file $file")
      val oldContent = IO.read(file)
      if (oldContent.indexOf(ifText) >= 0) {
        val content = oldContent.replaceAllLiterally(oldText, newText)
        if (oldContent != content) {
          IO.write(file, content)
          log.warn(s"Broken local repo contained $file with no ivy extra namespace in it, fixed via ridiculous hack")

          // fix the checksums
          val sha1File = new File(file.getPath + ".sha1")
          val md5File = new File(file.getPath + ".md5")
          md5File.delete()
          IO.write(sha1File, Hash.toHex(Hash(content)) + "\n")
          log.warn(s"Deleted $md5File and fixed $sha1File to reflect new $file")
          true
        } else false
      } else false
    }

    val didAnythings = for {
      f <- (dir.***).get
      if f.name == "ivy.xml"
    } yield replaceInFile(f,
                          "e:sbtTransformHash",
                          "xmlns:m=\"http://ant.apache.org/ivy/maven\">",
                          "xmlns:m=\"http://ant.apache.org/ivy/maven\" xmlns:e=\"http://ant.apache.org/ivy/extra\">",
                          s"Added missing ivy extra namespace to $f")
    if (!didAnythings.foldLeft(false)(_ || _))
      log.warn(s"No ivy.xml fixups, can remove this hack if clean;offlineTests works without it")
  }
}
