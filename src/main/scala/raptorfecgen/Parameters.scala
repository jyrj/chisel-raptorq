package raptorfecgen

import chisel3._

/**
  * Defines the configurable parameters for the RaptorQ FEC generator.
  *
  * This case class allows for specifying the geometry of the FEC code,
  * such as the number of source symbols, the symbol width, and the
  * number of repair symbols to generate.
  *
  * @param sourceK The number of source symbols in a source block. Per RFC 6330, this can be from 4 to 4096.
  * @param numParitySymbolsRS The number of parity symbols for the Reed-Solomon pre-code.
  * @param symbolBits The width of each symbol in bits. The initial hardware supports 8-bit symbols.
  * @param ltRepairCap The maximum number of LT repair symbols the generator's buffers can handle.
  */
case class RaptorFECParameters(
    // Codec Geometry
    sourceK: Int = 223,
    numParitySymbolsRS: Int = 32,
    symbolBits: Int = 8,
    ltRepairCap: Int = 50 // Increased default capacity
) {
  // --- Parameter Validation ---

  // Per the proposal, sourceK can range from 4 to 4096. [cite: 4]
  require(sourceK >= 4 && sourceK <= 4096, "sourceK must be between 4 and 4096.")

  // Per the proposal, symbolBits can be 8, 10, or 12. [cite: 4]
  // NOTE: The current GF256Multiplier module only supports symbolBits=8.
  // Using other values would require a different GF multiplier implementation.
  require(Set(8, 10, 12).contains(symbolBits), "Supported symbolBits are 8, 10, or 12.")

  // For the RS(N,K) code over GF(2^8), N must be <= 255.
  val totalSymbolsRS: Int = sourceK + numParitySymbolsRS
  if (symbolBits == 8) {
    require(totalSymbolsRS <= 255, s"For 8-bit symbols, N (sourceK + numParity) must be <= 255, but got ${totalSymbolsRS}")
  }
}

// Define an IO bundle for streaming data, similar to AXI-Stream (simplified)
class FECStream(private val p: RaptorFECParameters) extends Bundle {
  val data = UInt(p.symbolBits.W)
  val valid = Bool()
  val ready = Bool() // Input in Chisel
  val last = Bool()  // Indicates last symbol in a packet/block
}

object FECStream {
  def apply(p: RaptorFECParameters): FECStream = new FECStream(p)
}