lazy val commonSettings = Seq(
  scalaVersion := "2.12.8"
)

lazy val root = project.in(file("."))
  .aggregate(core)
  .settings(commonSettings)

lazy val core = project.in(file("org.scala-ide.sdt.core"))
  .settings(commonSettings)
  .settings(
    scalaSource in Compile := baseDirectory.value / "src",
    libraryDependencies ++= Seq(
      "org.scala-lang"        %   "scala-compiler" % scalaVersion.value,
      "org.scala-refactoring" %   "org.scala-refactoring.library_2.12.3"  % "0.13.0",
      "org.scalariform"       %%  "scalariform"                           % "0.2.6",
      "org.eclipse.platform"  %   "org.eclipse.core.runtime"              % "3.15.100",
      "org.eclipse.platform"  %   "org.eclipse.core.resources"            % "3.13.200",
      "org.eclipse.platform"  %   "org.eclipse.jface.text"                % "3.15.0"  exclude("org.eclipse.platform", "org.eclipse.swt.${osgi.platform}"),
      "org.eclipse.platform"  %   "org.eclipse.swt.gtk.linux.x86_64"      % "3.109.0" exclude("org.eclipse.platform", "org.eclipse.swt.${osgi.platform}"),
      "org.eclipse.platform"  %   "org.eclipse.equinox.app"               % "1.3.600",
      "org.eclipse.jdt"       %   "org.eclipse.jdt.core"                  % "3.16.0"
    )
  )
