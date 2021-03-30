package jtag2mm

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import chisel3.experimental._
import chisel3.iotesters.Driver
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}
import dsptools.DspTester

class JtagFuzzerTester(dut: JtagFuzzer) extends DspTester(dut) {//PeekPokeTester(dut) {

  step(10)
  //poke(dut.io.begin, 1)
  step(5)
  //poke(dut.io.begin, 0)
  step(2500)
}


class jtagFuzzerSpec extends FlatSpec with Matchers {
  //implicit val p: Parameters = Parameters.empty
  def dut(irLength: Int, beatBytes: Int, numOfTransfers: Int): () => JtagFuzzer = () => {
    new JtagFuzzer(irLength, beatBytes, numOfTransfers)
  }

  val beatBytes = 4
  val irLength = 4
  val numOfTransfers = 10
  
  it should "Test JTAG Fuzzer" in {
    //val dut = new JtagFuzzer(irLength, beatBytes)

    //chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => dut) { c =>
    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator"), dut(irLength, beatBytes, numOfTransfers)) { c =>
      new JtagFuzzerTester(c)
    } should be(true)
  }
}
