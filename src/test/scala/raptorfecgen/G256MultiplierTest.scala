package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class GF256MultiplierTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "GF256Multiplier"

  private val params = RaptorFECParameters()   // symbolBits = 8

  it should "multiply 0x53 by 0xCA to get 0x8F" in {
    test(new GF256Multiplier(params)) { dut =>
      dut.io.a.poke(0x53.U); dut.io.b.poke(0xCA.U); dut.clock.step()
      dut.io.product.expect(0x8F.U)              // RFC-6330 result
    }
  }

  it should "multiply by zero correctly" in {
    test(new GF256Multiplier(params)) { dut =>
      dut.io.a.poke(0x55.U); dut.io.b.poke(0x00.U); dut.clock.step()
      dut.io.product.expect(0x00.U)

      dut.io.a.poke(0x00.U); dut.io.b.poke(0xAA.U); dut.clock.step()
      dut.io.product.expect(0x00.U)
    }
  }

  it should "multiply by one correctly" in {
    test(new GF256Multiplier(params)) { dut =>
      dut.io.a.poke(0x55.U); dut.io.b.poke(0x01.U); dut.clock.step()
      dut.io.product.expect(0x55.U)

      dut.io.a.poke(0x01.U); dut.io.b.poke(0xAA.U); dut.clock.step()
      dut.io.product.expect(0xAA.U)
    }
  }

  it should "match the Scala reference model for a sample set" in {
    test(new GF256Multiplier(params)) { dut =>
      val vectors = Seq((0x02, 0x02), (0x02, 0x80), (0xFF, 0xFF), (0x1B, 0x2E))
      for ((a, b) <- vectors) {
        val expected = ScalaRSLTModel.gf256Multiply(a, b)
        dut.io.a.poke(a.U); dut.io.b.poke(b.U); dut.clock.step()
        dut.io.product.expect(expected.U, s"$a * $b failed")
      }
    }
  }
}
