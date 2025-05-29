// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.TestModule.ScalaTest
import scalalib._
// support BSP
import mill.bsp._

object RaptorFECGen extends SbtModule { m =>
  override def millSourcePath = os.pwd
  override def scalaVersion = "2.13.14"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit"
  )
  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:3.6.1",
  )
  // This line explicitly adds the Chisel compiler plugin for Mill and has been uncommented.
  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:3.6.1",
  )
  object test extends Tests with ScalaTest {
    override def ivyDeps = m.ivyDeps() ++ Agg(
      ivy"edu.berkeley.cs::chiseltest:0.6.2"
    )
  }
}