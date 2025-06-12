package rs

import raptorfecgen.RaptorFECParameters
import chisel3._
import chisel3.util.{log2Ceil, PopCount}

final case class RsParams(p: RaptorFECParameters) {
  // For now we hard-wire “classic” RS(255,223) on GF(2^8)
  val m:          Int = p.symbolBits                 // GF(2^m)
  val symbNum:    Int = 1 << m                       // 256
  val fieldChar:  Int = symbNum - 1                  // 255
  val k:          Int = p.sourceK                    // 223
  val n:          Int = p.totalSymbolsRS             // 255
  val redundancy: Int = n - k                        // 32
  val t:          Int = redundancy / 2               // # correctable symbols
  val busWidth:   Int = 1                            // 1-symbol per cycle for first cut
  val fcr:        Int = 0                            // first consecutive root
  val primitive:  Int = 0x11D                       // GF polynomial (RaptorQ / 0x11D)
}