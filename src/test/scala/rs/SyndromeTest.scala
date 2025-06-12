package rs
import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import raptorfecgen.{RaptorFECParameters, ScalaRSLTModel}

class SyndromeTest extends AnyFreeSpec with ChiselScalatestTester {
  "syndrome matches software model for RS(255,223)" in {
    val p   = RaptorFECParameters()
    val prm = RsParams(p)
    test(new Syndrome(prm)) { dut =>
      val msg = Seq.tabulate(prm.n)(i => (i * 3 + 7) & 0xff)  // some pseudo-random data

      /* feed symbols */
      for ((s, idx) <- msg.zipWithIndex) {
        dut.io.in.bits.poke(s.U)
        dut.io.in.valid.poke(true.B)
        dut.io.last.poke(idx == prm.n - 1)
        //while (!dut.io.in.ready.peekBoolean()) dut.clock.step()

        dut.clock.step()
      }
      dut.io.in.valid.poke(false.B)

      /* wait for syndrome */
      while (!dut.io.out.valid.peekBoolean()) dut.clock.step()
      val hwSyn = (0 until prm.redundancy).map(i => dut.io.out.bits(i).peekInt().toInt)

      /* software reference */
      val swSyn = {
        val a = 2
        (0 until prm.redundancy).map { sIdx =>
          val root = ScalaRSLTModel.gf256Multiply(1, 1)  // dummy just to bring symbolBits in scope
          val rootVal = ScalaRSLTModel.gf256Multiply(1, 1) // will fill below
          val rootPow = prm.fcr + sIdx
          val rVal = (0 until rootPow).foldLeft(1)((acc, _) => ScalaRSLTModel.gf256Multiply(acc, 2))
          msg.zipWithIndex.foldLeft(0) { case (acc, (sym, pos)) =>
            val pow = prm.n - 1 - pos
            val xk  = (0 until pow).foldLeft(1)((acc2, _) => ScalaRSLTModel.gf256Multiply(acc2, rVal))
            ScalaRSLTModel.gf256Multiply(sym, xk) ^ acc
          } & 0xff
        }
      }
      assert(hwSyn == swSyn)
    }
  }
}