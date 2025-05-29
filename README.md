# RAPTOR-FEC-GEN Course Project

This repository contains the proof-of-concept for `RAPTOR-FEC-GEN`, a Chisel generator for a streaming-ready, RaptorQ-compatible encoder and decoder. The goal of this project is to harden real-time video links against packet loss over IP networks.

## Current Status: Proof of Concept

This initial version establishes the core components required for the project's "Bootstrap Model".

* **Core Logic:** A fully verified, streaming-ready `GF(256)` multiplier is implemented. This is the fundamental building block for the Reed-Solomon pre-code stage of the RaptorQ FEC.
* **Structure:** Structural placeholders for the `RS(255,223)+LT` encoder and decoder are in place, ready for the full algorithm implementation.
* **Parameters:** The initial implementation targets 8-bit symbols and a single-lane architecture (`streamwidth=1`).
* **Verification:** The project uses `ChiselTest` for verification. All tests for the core multiplier and component instantiation are passing.

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
    The command will compile all Scala and Chisel source files. It will then execute the test suite in `src/test/scala/`. All tests should pass, confirming that the core `GF256Multiplier` is working correctly and the module hierarchy is sound.

## Next Steps

With the proof of concept validated, the next steps will follow the project roadmap:

1.  Implement the full RS and LT encoding/decoding logic.
2.  Parameterize key codec geometry values like `sourceK` and `repairCap`.
3.  Integrate a standard streaming interface like AXI-Stream.