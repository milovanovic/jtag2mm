# SPDX-License-Identifier: Apache-2.0


#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Tue Jan 26 11:20:55 2021

@author: vukand
"""


from pyftdi.gpio import GpioAsyncController

"""
0x01 - write instruction
0x02 - acquire address instruction
0x03 - acquire data instruction
0x04 - read instruction 

"""

#from pyftdi.ftdi import Ftdi
#Ftdi.show_devices()


def jtagInit(signalArray):
    signalArray = signalArray + [0x20, 0x30, 0x20, 0x30, 0x20, 0x30, 0x20, 
                                 0x30, 0x20, 0x30, 0x00, 0x10]
    return signalArray

def jtagSend(signalArray, data, dataLength, data_notInstruction):
    
    signalArray = signalArray + [0x20, 0x30]
    
    if (not data_notInstruction):
        signalArray = signalArray + [0x20, 0x30]
    
    signalArray = signalArray + [0x00, 0x10, 0x00, 0x10]
    
    for i in range(dataLength - 1):
        dataVal = ((data << 2) >> i) & 0x0004
        signalArray = signalArray + [(0x00 | (dataVal << 4)), (0x10 | (dataVal << 4))]
    
    dataVal = ((data << 2) >> (dataLength - 1)) & 0x0004
    signalArray = signalArray + [(0x20 | (dataVal << 4)), (0x30 | (dataVal << 4))]
    
    signalArray = signalArray + [0x20, 0x30, 0x00, 0x10]

    return signalArray


gpio = GpioAsyncController()
gpio.configure('ftdi://ftdi:232h:1:b/1', direction=0xF0)

signalArray = []
signalArray = jtagInit(signalArray)

#multiply enable
signalArray = jtagSend(signalArray, 0x03, 4, False)
signalArray = jtagSend(signalArray, 0x00000001, 32, True)
signalArray = jtagSend(signalArray, 0x02, 4, False)
signalArray = jtagSend(signalArray, 0x0000, 16, True)
signalArray = jtagSend(signalArray, 0x01, 4, False)

#multiplying factor
signalArray = jtagSend(signalArray, 0x03, 4, False)
signalArray = jtagSend(signalArray, 0x00001000, 32, True)
signalArray = jtagSend(signalArray, 0x02, 4, False)
signalArray = jtagSend(signalArray, 0x00000004, 32, True)
signalArray = jtagSend(signalArray, 0x01, 4, False)

#freq value
signalArray = jtagSend(signalArray, 0x03, 4, False)
signalArray = jtagSend(signalArray, 0x00000008, 32, True)
signalArray = jtagSend(signalArray, 0x02, 4, False)
signalArray = jtagSend(signalArray, 0x0000000C, 32, True)
signalArray = jtagSend(signalArray, 0x01, 4, False)

#nco enable
signalArray = jtagSend(signalArray, 0x03, 4, False)
signalArray = jtagSend(signalArray, 0x00000001, 32, True)
signalArray = jtagSend(signalArray, 0x02, 4, False)
signalArray = jtagSend(signalArray, 0x00000008, 32, True)
signalArray = jtagSend(signalArray, 0x01, 4, False)


gpio.write(signalArray)

gpio.close()
