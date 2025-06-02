package raptorfecgen

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * This test file serves as a demonstration of how to use the
  * parameterized `RaptorFECParameters` to create different hardware
  * configurations for the FEC modules.
  */
class ParameterizationTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "RaptorFECParameters Usage"

  // --- DEMO 1: Using the default parameters ---
  it should "instantiate modules with the default configuration" in {
    // 1. Create a parameter set. Using the defaults here.
    val defaultParams = RaptorFECParameters()

    // 2. Pass the parameters to the module constructor.
    //    We can check the parameters from within the DUT instance.
    test(new RSEncoder(defaultParams)) { dut =>
      println(s"DEMO 1: Instantiated RSEncoder with K=${dut.p.sourceK}, N-K=${dut.p.numParitySymbolsRS}")
      assert(dut.p.sourceK == 223)
      assert(dut.p.totalSymbolsRS == 255)
    }
  }

  // --- DEMO 2: Using a custom configuration ---
  it should "instantiate modules with a custom configuration" in {
    // 1. Define a custom set of parameters for a smaller block size.
    val customParams = RaptorFECParameters(
      sourceK = 64,
      numParitySymbolsRS = 16,
      ltRepairCap = 50
    )

    // 2. Pass the custom parameters to a different module.
    test(new LTEncoder(customParams)) { dut =>
      println(s"DEMO 2: Instantiated LTEncoder with K=${dut.p.sourceK}, RepairCap=${dut.p.ltRepairCap}")
      assert(dut.p.sourceK == 64)
      assert(dut.p.ltRepairCap == 50)
    }
  }

  // --- DEMO 3: Showing how invalid parameters are caught ---
  it should "fail to compile with invalid parameters" in {
    // The `require` statements in Parameters.scala act as guardrails.
    // If you try to create an invalid configuration, the generator will fail with an error.
    // Here, 240 + 32 > 255, which is invalid for 8-bit symbols.
    val caught = intercept[IllegalArgumentException] {
      // This line will throw an exception, which is caught by `intercept`.
      RaptorFECParameters(sourceK = 240)
    }
    println(s"DEMO 3: Correctly caught invalid parameter error: ${caught.getMessage}")
    assert(caught.getMessage.contains("must be <= 255"))
  }
}