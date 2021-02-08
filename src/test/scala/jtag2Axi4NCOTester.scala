// SPDX-License-Identifier: Apache-2.0

package jtag2mm

import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.experimental.{withClockAndReset}

import dsptools._
import dsptools.numbers._

import dspblocks._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

import chisel3.iotesters.Driver
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

import jtag._
import nco._


class jtag2Axi4NCOTester(dut: jtag2Axi4NCO[FixedPoint]) extends PeekPokeTester(dut.module) {
  
  def jtagReset(stepSize: Int = 1) {
    var i = 0
    while (i < 5) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      i += 1
    }
  }
  
  def jtagSend(data: BigInt, dataLength: Int, data_notInstruction: Boolean = true, state_reset_notIdle: Boolean = true, stepSize: Int = 1) {
    
    if (state_reset_notIdle) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 0)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
    }
    
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    
    if (!data_notInstruction) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
    }
    
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    
    var i = 0
    while (i < dataLength - 1) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 0)
      poke(dut.ioJTAG.jtag.TDI, data.testBit(i))
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      i += 1
    }
    
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 1)  // 0
    poke(dut.ioJTAG.jtag.TDI, data.testBit(i))
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 1)
    poke(dut.ioJTAG.jtag.TDI, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
  }
  
  val stepSize = 5//10000
  
  step(5)
  poke(dut.outStream.ready, 1)
  
  // non-burst write
  /*jtagReset(stepSize)
  jtagSend(BigInt("0010", 2), 4, false, true, stepSize)
  jtagSend(BigInt("0"*16, 2), 16, true, false, stepSize)
  jtagSend(BigInt("0011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*24 ++ "00000001", 2), 32, true, false, stepSize)
  jtagSend(BigInt("0001", 2), 4, false, false, stepSize)
  
  
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  
  jtagSend(BigInt("0011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*16 ++ "00010000" ++ "0"*8, 2), 32, true, false, stepSize)
  jtagSend(BigInt("0010", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*8 ++ "00000100", 2), 16, true, false, stepSize)
  jtagSend(BigInt("0001", 2), 4, false, false, stepSize)
  
  
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  
  jtagSend(BigInt("0011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*24 ++ "00000001", 2), 32, true, false, stepSize)
  jtagSend(BigInt("0010", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*8 ++ "00001000", 2), 16, true, false, stepSize)
  jtagSend(BigInt("0001", 2), 4, false, false, stepSize)
  
  
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)*/
  
  
  //burst write
  jtagReset(stepSize)
  jtagSend(BigInt("0010", 2), 4, false, true, stepSize)
  jtagSend(BigInt("0"*16, 2), 16, true, false, stepSize)
  jtagSend(BigInt("1000", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*6 ++ "11", 2), 8, true, false, stepSize)
  
  jtagSend(BigInt("1010", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*6 ++ "00", 2), 8, true, false, stepSize)
  jtagSend(BigInt("1011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*24 ++ "00000001", 2), 32, true, false, stepSize)
  
  jtagSend(BigInt("1010", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*6 ++ "01", 2), 8, true, false, stepSize)
  jtagSend(BigInt("1011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*16 ++ "00010000" ++ "0"*8, 2), 32, true, false, stepSize)
  
  jtagSend(BigInt("1010", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*6 ++ "10", 2), 8, true, false, stepSize)
  jtagSend(BigInt("1011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*24 ++ "00000001", 2), 32, true, false, stepSize)
  
  jtagSend(BigInt("1001", 2), 4, false, false, stepSize)
  
  
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  
  step(300)
  
  poke(dut.outStream.ready, 0)
  
  jtagSend(BigInt("0011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*24 ++ "00000100", 2), 32, true, false, stepSize)
  jtagSend(BigInt("0010", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*8 ++ "00001100", 2), 16, true, false, stepSize)
  jtagSend(BigInt("0001", 2), 4, false, false, stepSize)
  
  
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 1)
  step(stepSize)
  poke(dut.ioJTAG.jtag.TCK, 0)
  step(stepSize)
  
  // non-burst read
  //jtagSend(BigInt("0100", 2), 4, false, false, stepSize)
  
  
  // burst read
  jtagSend(BigInt("0010", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*16, 2), 16, true, false, stepSize)
  
  jtagSend(BigInt("1100", 2), 4, false, false, stepSize)
  
  var i = 0
  while(i < 16 * 3) {
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    step(stepSize)
    i += 1
  }
  
  poke(dut.outStream.ready, 1)
  
  step(300)

}


class jtag2Axi4NCOSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty
  
  val paramsNCO = FixedNCOParams(
    tableSize = 64,
    tableWidth = 16,
    phaseWidth = 8,
    rasterizedMode = false,
    nInterpolationTerms = 0,
    ditherEnable = false,
    syncROMEnable = false,
    phaseAccEnable = true,
    roundingMode = RoundHalfUp,
    pincType = Config,
    poffType = Fixed,
    useMultiplier = true,
    numMulPipes = 1,
    useQAM = false
  )
  
  val beatBytes = 4
  val irLength = 4
  val initialInstruction = BigInt("0", 2)
  val addresses = AddressSet(0x00000, 0xFFFF)
  val ncoAddress = AddressSet(0x0000, 0xFF)
  val maxBurstNum = 8
  
  it should "Test JTAG To AXI4 NCO" in {
    val lazyDut = LazyModule(new jtag2Axi4NCO(irLength, initialInstruction, beatBytes, addresses, maxBurstNum, paramsNCO, ncoAddress) {})

    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lazyDut.module) {
      c => new jtag2Axi4NCOTester(lazyDut)
    } should be (true)
  }
}




