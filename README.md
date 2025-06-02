# RAPTOR-FEC-GEN Course Project

This repository contains the proof-of-concept for `RAPTOR-FEC-GEN`, a Chisel generator for a streaming-ready, RaptorQ-compatible encoder and decoder. The goal of this project is to harden real-time video links against packet loss over IP networks.

## Current Status: Parameterized Bootstrap Model

This version completes the initial "Bootstrap Model" (`RS(255,223)+LT`) and introduces a flexible, parameterized generator. The full end-to-end encode and decode pipeline is functional and verified.

* **GF(256) Multiplier:** A fully implemented and verified logic-only `GF(256)` multiplier is complete. This is the fundamental building block for Reed-Solomon coding.
* **RS Encoder & Decoder:** A complete `RS(255, 223)` encoder and a functional `RSDecoder` are implemented. The system can successfully encode and decode error-free codewords.
* **LT Codec:** A functional `LTEncoder` and `LTDecoder` are implemented. They use a PRNG for symbol selection and can successfully encode and decode simple cases.
* **Flexible Parameters:** The generator is now parameterized. Key values like `sourceK`, `symbolBits`, and `ltRepairCap` can be easily configured, allowing for the creation of different FEC codec geometries.
* **Verification:** The project uses `ChiselTest`. The entire test suite, including a demonstration of the parameterization feature, passes successfully.

## How to Execute

To compile the code and run the verification suite, follow these steps.

1.  **Prerequisites:**

    * Java JDK
    * sbt (Scala Build Tool)

2.  **Clone the repository:**

    ```bash
    git clone https://github.com/jyrj/chisel-raptorq.git
    cd chisel-raptorq
    ```

3.  **Clean, Compile, and Test:**

    ```bash
    sbt clean test
    ```

4.  **Expected Outcome:**
    The command will compile all source files and execute the test suite. All tests should pass, confirming that the full `RS+LT` codec is functional and correctly parameterized.

## How to Use the Parameterized Generator

The hardware configuration is controlled by the `RaptorFECParameters` case class in `src/main/scala/raptorfecgen/Parameters.scala`.

1.  **Define a Configuration:** Create an instance of `RaptorFECParameters`, overriding any default values.

    ```scala
    // A custom configuration for a larger block size
    val customParams = RaptorFECParameters(
      sourceK = 1024,
      ltRepairCap = 200
    )
    ```

2.  **Instantiate a Module:** Pass the configuration object to the module's constructor.

    ```scala
    // This encoder will be built with sourceK=1024
    val myEncoder = Module(new RSEncoder(customParams))
    ```

A full, runnable demo can be found in `src/test/scala/raptorfecgen/ParameterizationTest.scala`.

## Next Steps

With the parameterized bootstrap model now validated, the project will proceed with the "Close-the-Loop" MVP goals:

1.  **Refactor to a Streaming Interface:** Convert the current block-based interfaces to a streaming architecture (e.g., AXI-Stream) to better suit real-time applications.
2.  **Enhance Decoder Robustness:** Implement the full mathematical algorithms (e.g., Berlekamp-Massey) in the `RSDecoder` and test the codec against more complex, realistic erasure patterns.
3.  **Add Reliability Features:** Begin implementing features like the optional `crcCheck` to improve data integrity.