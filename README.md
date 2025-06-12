# RAPTOR-FEC-GEN Course Project

This repository contains the final source code for `RAPTOR-FEC-GEN`, a Chisel-based generator for a streaming, RaptorQ-compatible Forward Error Correction (FEC) encoder and decoder. The project's goal is to create a configurable hardware IP block to protect real-time video streams from packet loss over IP networks. This implementation successfully builds and verifies a parameterized `RS(255,223)+LT` codec.

**For detailed information on the project's architecture, implementation, and original proposal, please visit the [official project Wiki.](https://github.com/jyrj/chisel-raptorq/wiki)**

## Final Project Status

The project successfully implements and verifies a complete RS+LT codec pipeline. It fulfills the core goals and introduces significant features from the original proposal's roadmap.

### Key Implemented Features:

* **Parameterized Generator**: The entire codec is a flexible generator. Key parameters such as `sourceK` (number of source symbols), `symbolBits`, and `ltRepairCap` (number of LT repair symbols) can be configured through the `RaptorFECParameters` class in `Parameters.scala`. This allows for the generation of various FEC codec geometries, a feature demonstrated and verified in the `ParametersTest.scala` test suite.

* **GF(256) Arithmetic Core**: A purely combinational, single-cycle Galois Field `GF(256)` multiplier forms the foundation of the Reed-Solomon codec. It requires no lookup tables and is thoroughly verified against examples from RFC 6330 and a Scala software model.

* **Streaming Reed-Solomon (RS) Codec**:
    * **Encoder**: A fully functional, streaming `RSEncoder` for the `RS(255, 223)` code is implemented. It accepts a stream of source symbols and appends the 32 parity symbols, as verified in `RSEncoderTest.scala`.
    * **Decoder**: The `RSDecoder` implements the complete pipeline for **error detection and location**. This includes:
        1.  Syndrome Calculation on the incoming stream.
        2.  **Berlekamp-Massey Algorithm**: Identifies the error locator polynomial (σ(x)) from the syndromes. `RSDecoderTest` confirms the decoder correctly reports the number of errors (L).
        3.  **Chien Search**: Finds the roots of the error locator polynomial to pinpoint the exact locations of the errors in the codeword.
    * **Note**: While the decoder successfully identifies the number and location of errors, the final error *value computation* (i.e., the Forney algorithm) and correction step is not implemented (implementing it as next step). The decoder currently flags an unrecoverable error if syndromes are non-zero.

* **Luby Transform (LT) Codec**: A functional `LTEncoder` and `LTDecoder` pair have been implemented and verified. The encoder uses a PRNG based on RFC 6330 to select source symbols for generating repair symbols. The `LTDecoder` successfully reconstructs the original source block from a stream of received symbols using an iterative belief propagation algorithm.

* **Verification**: The project is built with `sbt` and uses the `ChiselTest` framework for extensive verification. The entire test suite passes, confirming that all individual components and the end-to-end parameterization features are functional.

## How to Execute

To compile the Chisel source and run the complete verification suite, you need Java and sbt installed.

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/jyrj/chisel-raptorq.git
    cd chisel-raptorq
    ```

2.  **Clean, Compile, and Test:**
    The continuous integration pipeline uses this exact command to verify the project.
    ```bash
    sbt clean test
    ```

3.  **Expected Outcome:**
    The command will compile all Scala/Chisel source files and run the full test suite. All tests should pass, confirming that the parameterized `RS+LT` codec is functioning as expected.

## How to Use the Parameterized Generator

The hardware configuration is controlled by the `RaptorFECParameters` case class in `src/main/scala/raptorfecgen/Parameters.scala`.

1.  **Define a Configuration:** Create an instance of `RaptorFECParameters`, overriding any default values.

    ```scala
    // A custom configuration for a smaller block size
    val customParams = RaptorFECParameters(
      sourceK = 64,
      numParitySymbolsRS = 16,
      ltRepairCap = 30
    )
    ```

2.  **Instantiate a Module:** Pass the configuration object to any module's constructor.

    ```scala
    // This encoder will be built for a 64-symbol source block
    val myEncoder = Module(new RSEncoder(customParams))
    ```
A full, runnable demonstration can be found in `src/test/scala/raptorfecgen/ParametersTest.scala`.

## Future Work & Next Steps

The following steps would be required to make it a production-ready IP core:

1.  **Implement RS Error Correction**: The highest priority is to complete the `RSDecoder` by implementing the Forney algorithm to calculate and apply the error values at the locations found by the Chien search.

2.  **Transition to AXI-Stream**: Refactor the `DecoupledIO` interfaces to a standard, industry-friendly AXI-Stream interface with `TVALID/TREADY/TDATA/TLAST`, as envisioned in the project proposal.

3.  **Add Reliability Features**: Implement the optional reliability features from the proposal, such as a `crcCheck` for end-to-end data integrity.

4.  **Harden and Integrate**: Test the codec with real-world data, such as feeding it H.264 NAL units to verify erasure recovery in a realistic scenario.

5.  **Expand Feature Set**: Build upon the parameterized generator to support advanced features from the roadmap, including 10/12-bit symbols and multi-lane pipelines for higher throughput.
