package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

private object ReferenceRS {
  import RSEncoder.genCoeffs

  private def mul(a: Int, b: Int): Int = ScalaRSLTModel.gf256Multiply(a, b)

  /** Returns K+32 symbols (K source followed by 32 parity). */
  def encode(src: Seq[Int]): Seq[Int] = {
    require(src.length == 223)
    val parity = Array.fill(32)(0)
    for (byte <- src) {
      val fb = byte ^ parity(31)
      for (i <- 31 until 0 by -1)
        parity(i) = parity(i-1) ^ mul(fb, genCoeffs(31 - i))
      parity(0) = mul(fb, genCoeffs.last)
    }
    src ++ parity.reverse
  }
}

class RSEncoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RSEncoder"

  val p = RaptorFECParameters() // defaults to RS(255,223)

  it should "match the Scala reference model for random blocks" in {
    test(new RSEncoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      for (trial <- 0 until 10) {
        val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
        val golden  = ReferenceRS.encode(srcData)

        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.zip(srcData).foreach { case (port, v) => port.poke(v.U) }
        while (!dut.io.in.ready.peek().litToBoolean) { dut.clock.step() }
        dut.clock.step()                           // capture
        dut.io.in.valid.poke(false.B)

        while (!dut.io.out.valid.peek().litToBoolean) { dut.clock.step() }

        for (i <- 0 until p.totalSymbolsRS) {
          dut.io.out.bits(i).expect(golden(i).U, s"symbol $i mismatch on trial $trial")
        }

        dut.io.out.ready.poke(true.B); dut.clock.step()
        dut.io.out.ready.poke(false.B)
      }
    }
  }
}
