package raptorfecgen

import chisel3._
import chisel3.util._ // Required for DecoupledIO

/**
  * Defines the payload for our standard streaming interface.
  * Contains the symbol data and a 'last' signal to indicate end of a packet/block.
  */
class FECDataBundle(val p: RaptorFECParameters) extends Bundle {
  val data = UInt(p.symbolBits.W)
  val last = Bool()
}

/**
  * Defines the configurable parameters for the RaptorQ FEC generator.
  */
case class RaptorFECParameters(
    sourceK: Int = 223,
    numParitySymbolsRS: Int = 32,
    symbolBits: Int = 8,
    ltRepairCap: Int = 50
) {
  require(sourceK >= 4 && sourceK <= 4096, "sourceK must be between 4 and 4096.")
  require(Set(8, 10, 12).contains(symbolBits), "Supported symbolBits are 8, 10, or 12.")
  val totalSymbolsRS: Int = sourceK + numParitySymbolsRS
  if (symbolBits == 8) {
    require(totalSymbolsRS <= 255, s"For 8-bit symbols, N (sourceK + numParity) must be <= 255, but got ${totalSymbolsRS}")
  }
}