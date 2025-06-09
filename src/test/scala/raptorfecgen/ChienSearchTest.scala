package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ChienSearchTest extends AnyFlatSpec with ChiselScalatestTester {
  val p = RaptorFECParameters(sourceK = 223, numParitySymbolsRS = 32)
  val α  = GaloisField.alpha_powers
  def mul(a: Int, b: Int) = ScalaRSLTModel.gf256Multiply(a, b)

  /** σ(x) = (1 − α^{i1}x)(1 − α^{i2}x) */
  private def sigma(i1: Int, i2: Int): Seq[Int] = {
    val s1 = α(i1)
    val s2 = α(i2)
    val c1 = s1 ^ s2            // σ₁
    val c2 = mul(s1, s2)        // σ₂
    Seq(1, c1, c2) ++ Seq.fill(p.numParitySymbolsRS/2 - 2)(0)
  }

  "ChienSearch" should "flag roots at indices 3 and 42" in {
    val targetRoots = Set(3, 42)
    val coeffs      = sigma(3, 42)

    test(new ChienSearch(p)) { dut =>
      coeffs.zipWithIndex.foreach { case (v, i) => dut.io.sigma(i).poke(v.U) }

      dut.io.start.poke(true.B); dut.clock.step()
      dut.io.start.poke(false.B)

      while (!dut.io.done.peekBoolean()) dut.clock.step()

      val mask = dut.io.rootMask.map(_.peekBoolean())
      for (i <- 0 until p.totalSymbolsRS) {
        assert(mask(i) == targetRoots.contains(i),
          s"index $i root flag incorrect, expected=${targetRoots.contains(i)} got=${mask(i)}")
      }
    }
  }
}
