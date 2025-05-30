# RAPTOR-FEC-GEN Course Project

This repository contains the proof-of-concept for `RAPTOR-FEC-GEN`, a Chisel generator for a streaming-ready, RaptorQ-compatible encoder and decoder. The goal of this project is to harden real-time video links against packet loss over IP networks.

## Current Status: Week 1 Checkpoint

This version establishes the core encoding components required for the project's "Bootstrap Model" (`RS(255,223)+LT`).

  * **GF(256) Multiplier:** A fully implemented and verified logic-only `GF(256)` multiplier is complete. This is the fundamental building block for Reed-Solomon coding.
  * **RS Encoder:** A complete `RS(255, 223)` encoder is implemented and verified against a reference software model using randomized data. The current implementation uses a block-based interface.
  * **Placeholders:** Structural placeholders for the `RSDecoder` and the `LT` Codec are in place for future implementation.
  * **Verification:** The project uses `ChiselTest`. All existing tests for the multiplier and encoder pass successfully.

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
    The command will compile all source files and execute the test suite. All tests should pass, confirming that the `GF256Multiplier` and the `RSEncoder` are fully functional and correct.

## Next Steps

With the `RSEncoder` validated, the next steps will follow the project roadmap:

1.  Implement the full `RSDecoder` logic.
2.  Implement the `LTEncoder` and `LTDecoder` using a pseudo-random number generator (PRNG).
3.  Parameterize key codec geometry values like `sourceK` and `repairCap`.
4.  (Optional) Refactor the block-based `RSEncoder` to a streaming interface to align with the final performance goals.