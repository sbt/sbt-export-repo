sbt-export-repo
===============

sbt-export-repo exports your dependency graph to a preloaded local repository.

This is an experimental plugin provided as-is.

setup
-----

Add the following to `project/exportrepo.sbt`:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-export-repo" % "0.1.0")
```

Then, create `dist` subproject in `build.sbt`:

```scala
// Your normal subproject
lazy val app = (project in file("app"))

// This subproject is used for exporting repo only
lazy val dist = (project in file("dist")).
  enablePlugins(ExportRepoPlugin).
  dependsOn(app). // add your subprojects to export
  settings(
    commonSettings,
    name := "dist",
    // add external libraries too. why not?
    libraryDependencies += "org.typelevel" %% "cats" % "0.6.0",
    publish := (),
    publishLocal := ()
  )
```

usage
-----

```scala
> publishLocal
> dist/exportRepo
```

This will create an Ivy repo image under `dist/target/preloaded-local` containing the transitive dependencies of both the internal and external dependencies you added to `dist` (e.g. `app` and Cats).

credits
-------

- Export repo feature was extracted from Lightbend Activator's build, in particular [typesafehub/activator@03f2e3][1] by [@jsuereth][@jsuereth]

### license

Apache-2.0

  [1]: https://github.com/typesafehub/activator/commit/03f2e315011ce43e13e8ac5714a28c0fea0c73c6
  [@jsuereth]: https://github.com/jsuereth
