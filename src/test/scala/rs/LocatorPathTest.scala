package rs

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import raptorfecgen.RaptorFECParameters

class LocatorPathTest extends AnyFreeSpec with ChiselScalatestTester {

  "syndrome block still compiles and runs inside a tiny system" in {
    val prm = RsParams(RaptorFECParameters())   // RS(255,223)

    test(new Module {
      val io = IO(new Bundle {
        val run  = Input(Bool())
        val done = Output(Bool())
      })

      /* build a 255-byte message, each value < 256 */
      val msgROM = VecInit(
        (0 until prm.n).map(i => ((i * 11 + 7) & 0xFF).U(8.W))
      )

      val synd = Module(new Syndrome(prm))

      val cnt  = RegInit(0.U(9.W))
      val send = io.run && (cnt < prm.n.U)
      when(send) { cnt := cnt + 1.U }

      synd.io.in.valid := send
      synd.io.in.bits  := msgROM(cnt)
      synd.io.last     := cnt === (prm.n - 1).U

      io.done := RegNext(synd.io.out.valid, init = false.B)
    }) { dut =>
      dut.io.run.poke(true.B)

      /* wait up to 400 cycles for the done-pulse */
      var cycles = 0
      while (!dut.io.done.peekBoolean() && cycles < 400) {
        dut.clock.step()
        cycles += 1
      }
      dut.io.done.expect(true.B)          // must have occurred
    }
  }
}