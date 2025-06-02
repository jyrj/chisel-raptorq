package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RSDecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RSDecoder"

  val p = RaptorFECParameters() // defaults to RS(255,223)

  it should "recover an error-free codeword" in {
    test(new RSDecoder(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val srcData = Seq.fill(p.sourceK)(scala.util.Random.nextInt(256))
      val encodedData = ReferenceRS.encode(srcData)

      // Input the codeword to the decoder
      dut.io.in.valid.poke(true.B)
      dut.io.in.bits.zip(encodedData).foreach { case (port, v) => port.poke(v.U) }
      dut.io.erasures.foreach(_.poke(false.B))
      dut.clock.step(1)
      dut.io.in.valid.poke(false.B)

      // Wait for the decoder to finish
      dut.io.out.ready.poke(true.B)
      var timeout = 0
      while (!dut.io.out.valid.peek().litToBoolean && timeout < 50) {
        dut.clock.step(1)
        timeout = timeout + 1
      }
      
      // Check the output
      dut.io.out.valid.expect(true.B)
      dut.io.error.expect(false.B, "Decoder flagged an error on a valid codeword")
      dut.io.out.bits.zip(srcData).foreach { case (port, v) =>
        port.expect(v.U)
      }
      
      dut.clock.step(1)
    }
  }
}