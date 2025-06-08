package raptorfecgen

/**
  * A software reference model for testing FEC components.
  */
object ScalaRSLTModel {
  def gf256Multiply(a: Int, b: Int): Int = {
    val P = 0x11D // Primitive polynomial for GF(2^8)
    var res = 0
    var temp_a = a
    var temp_b = b
    for (i <- 0 until 8) {
      if ((temp_b & 1) != 0) res = res ^ temp_a
      val msb_set = (temp_a & 0x80) != 0
      temp_a = temp_a << 1
      if (msb_set) temp_a = temp_a ^ P
      temp_b = temp_b >> 1
    }
    res & 0xFF
  }

  // --- Additions for gfInv ---
  private val alphaPowArr: Array[Int] = Array.tabulate(256)(i => {
    var pval = 1
    if (i > 0) { for (_ <- 0 until i) { pval = gf256Multiply(pval, 0x02) } } // Use gf256Multiply from this object
    pval
  })

  private val alphaLogArr: Array[Int] = {
    val log = Array.fill(256)(0)
    for(i <- 0 until 255) { // alpha^255 is typically not defined in log table, or is 0. Log of 0 is undefined.
        if (alphaPowArr(i) != 0) log(alphaPowArr(i)) = i
    }
    log(1) = 0 // Explicitly alpha^0 = 1, so log(1) = 0. Some tables might set log(alphaPow(255)) if alpha^255=1
    log
  }

  def gfInv(a: Int): Int = {
    if (a == 0) 0 // Inverse of 0 is typically 0 in FEC contexts, or an error
    else alphaPowArr(255 - alphaLogArr(a)) // alpha^(-x) = alpha^(255-x) in GF(2^8)
  }
  // --- End of Additions ---

  // ... other existing placeholder functions in ScalaRSLTModel can remain ...
}

/**
  * Reference implementation for the RS(255,223) encoder.
  * Now lives in this file to be accessible by all tests.
  */
object ReferenceRS {
  import RSEncoder.genCoeffs // Assuming RSEncoder object with genCoeffs is accessible
  private def mul(a: Int, b: Int): Int = ScalaRSLTModel.gf256Multiply(a, b)
  def encode(src: Seq[Int]): Seq[Int] = {
    require(src.length == 223, s"Input length for RS encode must be 223, got ${src.length}")
    val parity = Array.fill(32)(0) // For RS(255,223), there are 255-223 = 32 parity symbols
    for (byte_val <- src) { // Renamed to avoid conflict if byte is a keyword/type
      val fb = byte_val ^ parity(31)
      for (i <- 31 until 0 by -1) { // Corrected loop range
        parity(i) = parity(i-1) ^ mul(fb, genCoeffs(31 - i)) // Check genCoeffs indexing
      }
      parity(0) = mul(fb, genCoeffs.last) // Check genCoeffs definition
    }
    src ++ parity.reverse // Parity typically appended
  }
}