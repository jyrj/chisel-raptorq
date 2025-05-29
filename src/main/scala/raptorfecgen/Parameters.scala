package raptorfecgen

import chisel3._

// Initial Parameters based on the proposal [cite: 4, 5]
case class RaptorFECParameters(
    // Codec Geometry
    sourceK: Int = 223,         // Number of source symbols for RS(255,223) [cite: 5]
                                // Can be parameterized later from 4-4096 [cite: 4]
    numParitySymbolsRS: Int = 32, // For RS(255,223), N-K = 255-223 = 32 parity symbols
    symbolBits: Int = 8,        // Symbol width in bits, initial model uses 8-bit symbols [cite: 5]
    // streamWidth: Int = 1,       // Symbols per cycle, initial model is single-lane [cite: 6]

    // LT specific parameters (placeholders for now)
    ltRepairCap: Int = 10 // Example, max repair symbols for LT
) {
  require(symbolBits == 8, "Initial model only supports 8-bit symbols")
  // require(streamWidth == 1, "Initial model only supports streamWidth = 1")
  require(sourceK > 0 && sourceK <= 4096)
  val totalSymbolsRS: Int = sourceK + numParitySymbolsRS // N for RS code, e.g., 255
  // Ensure N is suitable for GF(2^8)
  require(totalSymbolsRS <= 255, "For 8-bit symbols, N must be <= 255")
}

// Define an IO bundle for streaming data, similar to AXI-Stream (simplified)
class FECStream(private val p: RaptorFECParameters) extends Bundle {
  val data = UInt((p.symbolBits).W) // For streamWidth=1, one symbol at a time
  val valid = Bool()
  val ready = Bool() // Input in Chisel
  val last = Bool()  // Indicates last symbol in a packet/block
}

object FECStream {
  def apply(p: RaptorFECParameters): FECStream = new FECStream(p)
}