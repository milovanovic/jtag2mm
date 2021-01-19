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


class jtag2TLPassthroughTester(dut: jtag2TLPassthrough) extends PeekPokeTester(dut.module) {
  
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
  
  val stepSize = 5
  
  step(5)
  poke(dut.outStream.ready, 1)
  
  jtagReset(stepSize)
  jtagSend(BigInt("010", 2), 3, false, true, stepSize)
  jtagSend(BigInt("0"*16, 2), 16, true, false, stepSize)
  jtagSend(BigInt("011", 2), 3, false, false, stepSize)
  jtagSend(BigInt("0"*56 ++ "00001000", 2), 64, true, false, stepSize)
  jtagSend(BigInt("001", 2), 3, false, false, stepSize)
  
  step(100)
  
  poke(dut.outStream.ready, 0)
  
  jtagReset(stepSize)
  jtagSend(BigInt("010", 2), 3, false, true, stepSize)
  jtagSend(BigInt("0"*16, 2), 16, true, false, stepSize)
  jtagSend(BigInt("011", 2), 3, false, false, stepSize)
  jtagSend(BigInt("0"*56 ++ "00011000", 2), 64, true, false, stepSize)
  jtagSend(BigInt("001", 2), 3, false, false, stepSize)
  
  
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
  
  jtagSend(BigInt("100", 2), 3, false, false, stepSize)
  
  var i = 0
  while(i < 32) {
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


class jtag2TLPassthroughSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty
  
  
  val params = PassthroughParams(depth = 0)
  val irLength = 3
  val initialInstruction = BigInt("0", 2)
  val addresses = AddressSet(0x00000, 0xFFFF)
  val beatBytes = 8
  
  it should "Test JTAG To TL Passthrough" in {
    val lazyDut = LazyModule(new jtag2TLPassthrough(irLength, initialInstruction, beatBytes, addresses, params) {})

    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lazyDut.module) {
      c => new jtag2TLPassthroughTester(lazyDut)
    } should be (true)
  }
}




