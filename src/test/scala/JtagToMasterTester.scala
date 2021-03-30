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

class TLJTAGToMasterBlockTester(dut: TLJTAGToMasterBlock) extends PeekPokeTester(dut.module) {

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

  def jtagSend(
    data:                BigInt,
    dataLength:          Int,
    dataNotInstruction: Boolean = true,
    stateResetNotIdle: Boolean = true,
    stepSize:            Int = 1
  ) {

    if (stateResetNotIdle) {
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

    if (!dataNotInstruction) {
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
    //while (i < instructionLength) {
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
    poke(dut.ioJTAG.jtag.TMS, 1) // 0
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
    /*poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)*/
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
  }

  val stepSize = 5

  step(5)
  poke(dut.ioTL.a.ready, 1)

  jtagReset(stepSize)
  jtagSend(BigInt("010", 2), 3, false, true, stepSize)
  jtagSend(BigInt("0" * 8 ++ "01010000", 2), 16, true, false, stepSize)
  jtagSend(BigInt("011", 2), 3, false, false, stepSize)
  jtagSend(BigInt("00101000", 2), 8, true, false, stepSize)
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

  step(10)
  poke(dut.ioTL.d.valid, 1)
  poke(dut.ioTL.d.bits.opcode, 0)
  poke(dut.ioTL.d.bits.source, 1)
  poke(dut.ioTL.d.bits.size, 2)
  step(1)
  poke(dut.ioTL.d.valid, 0)
  step(10)

  jtagSend(BigInt("011", 2), 3, false, false, stepSize)
  jtagSend(BigInt("01101111", 2), 8, true, false, stepSize)
  jtagSend(BigInt("010", 2), 3, false, false, stepSize)
  jtagSend(BigInt("0" * 8 ++ "01110000", 2), 16, true, false, stepSize)
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

  step(10)
  poke(dut.ioTL.d.valid, 1)
  poke(dut.ioTL.d.bits.opcode, 0)
  poke(dut.ioTL.d.bits.source, 1)
  poke(dut.ioTL.d.bits.size, 2)
  step(1)
  poke(dut.ioTL.d.valid, 0)
  step(10)

  jtagSend(BigInt("100", 2), 3, false, false, stepSize)
  step(10)
  poke(dut.ioTL.d.valid, 1)
  poke(dut.ioTL.d.bits.opcode, 1)
  poke(dut.ioTL.d.bits.source, 1)
  poke(dut.ioTL.d.bits.size, 2)
  poke(dut.ioTL.d.bits.data, 111)
  step(1)
  poke(dut.ioTL.d.valid, 0)
  step(10)

  var i = 0
  while (i < 4) {
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

}

class AXI4JTAGToMasterBlockTester(dut: AXI4JTAGToMasterBlock) extends PeekPokeTester(dut.module) {

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

  def jtagSend(
    data:                BigInt,
    dataLength:          Int,
    dataNotInstruction: Boolean = true,
    stateResetNotIdle: Boolean = true,
    stepSize:            Int = 1
  ) {

    if (stateResetNotIdle) {
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

    if (!dataNotInstruction) {
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
    poke(dut.ioJTAG.jtag.TMS, 1) // 0
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
  poke(dut.ioAXI4.w.ready, 1)
  poke(dut.ioAXI4.aw.ready, 1)

  jtagReset(stepSize)
  jtagSend(BigInt("010", 2), 3, false, true, stepSize)
  jtagSend(BigInt("0" * 8 ++ "01010000", 2), 16, true, false, stepSize)
  jtagSend(BigInt("011", 2), 3, false, false, stepSize)
  jtagSend(BigInt("00101000", 2), 8, true, false, stepSize)
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

  step(10)
  poke(dut.ioAXI4.b.valid, 1)
  poke(dut.ioAXI4.b.bits.resp, 0)
  step(1)
  poke(dut.ioAXI4.b.valid, 0)
  step(10)

  jtagSend(BigInt("011", 2), 3, false, false, stepSize)
  jtagSend(BigInt("01101111", 2), 8, true, false, stepSize)
  jtagSend(BigInt("010", 2), 3, false, false, stepSize)
  jtagSend(BigInt("0" * 8 ++ "01110000", 2), 16, true, false, stepSize)
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

  step(10)
  poke(dut.ioAXI4.b.valid, 1)
  poke(dut.ioAXI4.b.bits.resp, 0)
  step(1)
  poke(dut.ioAXI4.b.valid, 0)
  step(10)

}

class TLJTAGToMasterBlockSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  val beatBytes = 4
  val irLength = 3
  val initialInstruction = BigInt("0", 2)
  val addresses = AddressSet(0x00000, 0xffff)

  it should "Test TL JTAG" in {
    val lazyDut = LazyModule(new TLJTAGToMasterBlock(irLength, initialInstruction, beatBytes, addresses) {})

    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lazyDut.module) { c =>
      new TLJTAGToMasterBlockTester(lazyDut)
    } should be(true)
  }
}

class AXI4JTAGToMasterBlockSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  val beatBytes = 4
  val irLength = 3
  val initialInstruction = BigInt("0", 2)
  val addresses = AddressSet(0x00000, 0xffff)

  it should "Test AXI4 JTAG" in {
    val lazyDut = LazyModule(new AXI4JTAGToMasterBlock(irLength, initialInstruction, beatBytes, addresses) {})

    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lazyDut.module) { c =>
      new AXI4JTAGToMasterBlockTester(lazyDut)
    } should be(true)
  }
}
