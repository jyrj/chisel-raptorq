package raptorq

/** Minimal param bundle for Week-1 milestone. */
case class RaptorParams(
  sourceK:      Int = 223,   // RS(255,223) payload
  repairCap:    Int = 32,    // parity symbols initially = 255-223
  symbolBits:   Int = 8,     // GF(2^8)
  streamWidth:  Int = 1,     // symbols per cycle (1 for now)
  pipelineRegs: Int = 0      // optional timing regs (0 for now)
)
