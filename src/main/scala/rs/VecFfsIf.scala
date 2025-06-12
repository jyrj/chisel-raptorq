package rs
import chisel3._

class VecFfsIf(width: Int, symBits: Int) extends Bundle {
  val vec = Vec(width, UInt(symBits.W))
  val ffs = UInt(width.W)               // one–hot flags for valid entries
}