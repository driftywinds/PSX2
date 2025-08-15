//
// Created by k2154 on 2025-07-30.
//

#ifndef PCSX2_CPUREGISTERSPACK_H
#define PCSX2_CPUREGISTERSPACK_H

#include "R5900Def.h"
#include "R3000ADef.h"
#include "VUDef.h"

struct cpuRegistersPack
{
    alignas(16) cpuRegisters cpuRegs{};
    alignas(16) fpuRegisters fpuRegs{};
    alignas(16) psxRegisters psxRegs{};
    alignas(16) VURegs vuRegs[2];
    alignas(16) VIFregisters vifRegs[2];
    alignas(16) mVU_SSE4 mVUss4;
    alignas(32) mVU_Globals mVUglob;
};
alignas(32) extern cpuRegistersPack g_cpuRegistersPack;
////
static cpuRegisters& cpuRegs = g_cpuRegistersPack.cpuRegs;
static fpuRegisters& fpuRegs = g_cpuRegistersPack.fpuRegs;
static psxRegisters& psxRegs = g_cpuRegistersPack.psxRegs;
static VURegs& VU0 = g_cpuRegistersPack.vuRegs[0];
static VURegs& VU1 = g_cpuRegistersPack.vuRegs[1];
static mVU_SSE4& mVUClamp = g_cpuRegistersPack.mVUss4;
static mVU_Globals& mVUglob = g_cpuRegistersPack.mVUglob;

#endif //PCSX2_CPUREGISTERSPACK_H
