package raptorq
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import chisel3._

class RSEncoderSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RSEncoderHW"

  it should "match the Scala reference for one block" in {
    val p = RaptorParams()
    test(new RSEncoderHW(p)) { dut =>
      /* ——— disable idle-cycle watchdog ——— */
      dut.clock.setTimeout(0)

      val rnd    = new scala.util.Random(0xC0FFEE)
      val data   = Seq.fill(p.sourceK)(rnd.nextInt(256))
      val golden = RSEncoderModel.encode(data)
      val recv   = collection.mutable.ArrayBuffer[Int]()

      dut.io.codeOut.ready.poke(true.B)
      dut.io.start.poke(true.B); dut.clock.step(); dut.io.start.poke(false.B)

      /* feed all 223 data symbols */
      data.foreach { b =>
        while(!dut.io.dataIn.ready.peekBoolean()) dut.clock.step()
        dut.io.dataIn.valid.poke(true.B)
        dut.io.dataIn.bits .poke(b.U)
        dut.clock.step()
        dut.io.dataIn.valid.poke(false.B)

        if (dut.io.codeOut.valid.peekBoolean())
          recv += dut.io.codeOut.bits.peekInt().toInt
      }

      /* drain parity */
      while (recv.length < golden.length) {
        if (dut.io.codeOut.valid.peekBoolean())
          recv += dut.io.codeOut.bits.peekInt().toInt
        dut.clock.step()
      }

      assert(recv == golden)
    }
  }
}
