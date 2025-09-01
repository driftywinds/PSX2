// SPDX-FileCopyrightText: 2002-2025 PCSX2 Dev Team
// SPDX-License-Identifier: GPL-3.0

#include "Vif_UnpackNEON.h"
#include "common/Perf.h"
#include "Vif_Unpack.h"

// If building for ARM64 with NEON support, include the intrinsics header.  These
// definitions are used by the optional helper functions implemented below.
#ifdef __ARM_NEON
#include <arm_neon.h>
#endif

namespace a64 = vixl::aarch64;

// =====================================================================================================
//  VifUnpackSSE_Base Section
// =====================================================================================================
VifUnpackNEON_Base::VifUnpackNEON_Base()
	: usn(false)
	, doMask(false)
	, UnpkLoopIteration(0)
	, UnpkNoOfIterations(0)
	, IsAligned(0)
	, dstIndirect(a64::MemOperand(RXARG1))
	, srcIndirect(a64::MemOperand(RXARG2))
	, workReg(a64::q1)
	, destReg(a64::q0)
	, workGprW(a64::w4)
{
}

void VifUnpackNEON_Base::xMovDest() const
{
	if (!IsWriteProtectedOp())
	{
		if (IsUnmaskedOp())
			armAsm->Str(destReg, dstIndirect);
		else
			doMaskWrite(destReg);
	}
}

void VifUnpackNEON_Base::xShiftR(const vixl::aarch64::VRegister& regX, int n) const
{
	if (usn)
		armAsm->Ushr(regX.V4S(), regX.V4S(), n);
	else
		armAsm->Sshr(regX.V4S(), regX.V4S(), n);
}

void VifUnpackNEON_Base::xPMOVXX8(const vixl::aarch64::VRegister& regX) const
{
	// TODO(Stenzek): Check this
	armAsm->Ldr(regX.S(), srcIndirect);

	if (usn)
	{
		armAsm->Ushll(regX.V8H(), regX.V8B(), 0);
		armAsm->Ushll(regX.V4S(), regX.V4H(), 0);
	}
	else
	{
		armAsm->Sshll(regX.V8H(), regX.V8B(), 0);
		armAsm->Sshll(regX.V4S(), regX.V4H(), 0);
	}
}

void VifUnpackNEON_Base::xPMOVXX16(const vixl::aarch64::VRegister& regX) const
{
	armAsm->Ldr(regX.D(), srcIndirect);

	if (usn)
		armAsm->Ushll(regX.V4S(), regX.V4H(), 0);
	else
		armAsm->Sshll(regX.V4S(), regX.V4H(), 0);
}

void VifUnpackNEON_Base::xUPK_S_32() const
{
	if (UnpkLoopIteration == 0)
		armAsm->Ldr(workReg, srcIndirect);

	if (IsInputMasked())
		return;

	switch (UnpkLoopIteration)
	{
		case 0:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 0);
			break;
		case 1:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 1);
			break;
		case 2:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 2);
			break;
		case 3:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 3);
			break;
	}
}

void VifUnpackNEON_Base::xUPK_S_16() const
{
	if (UnpkLoopIteration == 0)
		xPMOVXX16(workReg);

	if (IsInputMasked())
		return;

	switch (UnpkLoopIteration)
	{
		case 0:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 0);
			break;
		case 1:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 1);
			break;
		case 2:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 2);
			break;
		case 3:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 3);
			break;
	}
}

void VifUnpackNEON_Base::xUPK_S_8() const
{
	if (UnpkLoopIteration == 0)
		xPMOVXX8(workReg);

	if (IsInputMasked())
		return;

	switch (UnpkLoopIteration)
	{
		case 0:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 0);
			break;
		case 1:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 1);
			break;
		case 2:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 2);
			break;
		case 3:
			armAsm->Dup(destReg.V4S(), workReg.V4S(), 3);
			break;
	}
}

// The V2 + V3 unpacks have freaky behaviour, the manual claims "indeterminate".
// After testing on the PS2, it's very much determinate in 99% of cases
// and games like Lemmings, And1 Streetball rely on this data to be like this!
// I have commented after each shuffle to show what data is going where - Ref

void VifUnpackNEON_Base::xUPK_V2_32() const
{
	if (UnpkLoopIteration == 0)
	{
		armAsm->Ldr(workReg, srcIndirect);

		if (IsInputMasked())
			return;

		armAsm->Dup(destReg.V2D(), workReg.V2D(), 0); //v1v0v1v0
		if (IsAligned)
			armAsm->Ins(destReg.V4S(), 3, a64::wzr); //zero last word - tested on ps2
	}
	else
	{
		if (IsInputMasked())
			return;

		armAsm->Dup(destReg.V2D(), workReg.V2D(), 1); //v3v2v3v2
		if (IsAligned)
			armAsm->Ins(destReg.V4S(), 3, a64::wzr); //zero last word - tested on ps2
	}
}

void VifUnpackNEON_Base::xUPK_V2_16() const
{
	if (UnpkLoopIteration == 0)
	{
		xPMOVXX16(workReg);

		if (IsInputMasked())
			return;

		armAsm->Dup(destReg.V2D(), workReg.V2D(), 0); //v1v0v1v0
	}
	else
	{
		if (IsInputMasked())
			return;

		armAsm->Dup(destReg.V2D(), workReg.V2D(), 1); //v3v2v3v2
	}
}

void VifUnpackNEON_Base::xUPK_V2_8() const
{
	if (UnpkLoopIteration == 0)
	{
		xPMOVXX8(workReg);

		if (IsInputMasked())
			return;

		armAsm->Dup(destReg.V2D(), workReg.V2D(), 0); //v1v0v1v0
	}
	else
	{
		if (IsInputMasked())
			return;

		armAsm->Dup(destReg.V2D(), workReg.V2D(), 1); //v3v2v3v2
	}
}

void VifUnpackNEON_Base::xUPK_V3_32() const
{
	if (IsInputMasked())
		return;

	armAsm->Ldr(destReg, srcIndirect);
	if (UnpkLoopIteration != IsAligned)
		armAsm->Ins(destReg.V4S(), 3, a64::wzr);
}

void VifUnpackNEON_Base::xUPK_V3_16() const
{
	if (IsInputMasked())
		return;

	xPMOVXX16(destReg);

	//With V3-16, it takes the first vector from the next position as the W vector
	//However - IF the end of this iteration of the unpack falls on a quadword boundary, W becomes 0
	//IsAligned is the position through the current QW in the vif packet
	//Iteration counts where we are in the packet.
	int result = (((UnpkLoopIteration / 4) + 1 + (4 - IsAligned)) & 0x3);

	if ((UnpkLoopIteration & 0x1) == 0 && result == 0)
		armAsm->Ins(destReg.V4S(), 3, a64::wzr); //zero last word on QW boundary if whole 32bit word is used - tested on ps2
}

void VifUnpackNEON_Base::xUPK_V3_8() const
{
	if (IsInputMasked())
		return;

	xPMOVXX8(destReg);
	if (UnpkLoopIteration != IsAligned)
		armAsm->Ins(destReg.V4S(), 3, a64::wzr);
}

void VifUnpackNEON_Base::xUPK_V4_32() const
{
	if (IsInputMasked())
		return;

	armAsm->Ldr(destReg.Q(), a64::MemOperand(srcIndirect));
}

void VifUnpackNEON_Base::xUPK_V4_16() const
{
	if (IsInputMasked())
		return;

	xPMOVXX16(destReg);
}

void VifUnpackNEON_Base::xUPK_V4_8() const
{
	if (IsInputMasked())
		return;

	xPMOVXX8(destReg);
}

void VifUnpackNEON_Base::xUPK_V4_5() const
{
	if (IsInputMasked())
		return;

	armAsm->Ldrh(workGprW, srcIndirect);
	armAsm->Lsl(workGprW, workGprW, 3); // ABG|R5.000
	armAsm->Dup(destReg.V4S(), workGprW); // x|x|x|R
	armAsm->Lsr(workGprW, workGprW, 8); // ABG
	armAsm->Lsl(workGprW, workGprW, 3); // AB|G5.000
	armAsm->Ins(destReg.V4S(), 1, workGprW); // x|x|G|R
	armAsm->Lsr(workGprW, workGprW, 8); // AB
	armAsm->Lsl(workGprW, workGprW, 3); // A|B5.000
	armAsm->Ins(destReg.V4S(), 2, workGprW); // x|B|G|R
	armAsm->Lsr(workGprW, workGprW, 8); // A
	armAsm->Lsl(workGprW, workGprW, 7); // A.0000000
	armAsm->Ins(destReg.V4S(), 3, workGprW); // A|B|G|R
	armAsm->Shl(destReg.V4S(), destReg.V4S(), 24); // can optimize to
	armAsm->Ushr(destReg.V4S(), destReg.V4S(), 24); // single AND...
}

void VifUnpackNEON_Base::xUnpack(int upknum) const
{
	switch (upknum)
	{
		case 0:
			xUPK_S_32();
			break;
		case 1:
			xUPK_S_16();
			break;
		case 2:
			xUPK_S_8();
			break;

		case 4:
			xUPK_V2_32();
			break;
		case 5:
			xUPK_V2_16();
			break;
		case 6:
			xUPK_V2_8();
			break;

		case 8:
			xUPK_V3_32();
			break;
		case 9:
			xUPK_V3_16();
			break;
		case 10:
			xUPK_V3_8();
			break;

		case 12:
			xUPK_V4_32();
			break;
		case 13:
			xUPK_V4_16();
			break;
		case 14:
			xUPK_V4_8();
			break;
		case 15:
			xUPK_V4_5();
			break;

		case 3:
		case 7:
		case 11:
			// TODO: Needs hardware testing.
			// Dynasty Warriors 5: Empire  - Player 2 chose a character menu.
			Console.Warning("Vpu/Vif: Invalid Unpack %d", upknum);
			break;
	}
}

// =====================================================================================================
//  VifUnpackSSE_Simple
// =====================================================================================================

VifUnpackNEON_Simple::VifUnpackNEON_Simple(bool usn_, bool domask_, int curCycle_)
{
	curCycle = curCycle_;
	usn = usn_;
	doMask = domask_;
	IsAligned = true;
}

void VifUnpackNEON_Simple::doMaskWrite(const vixl::aarch64::VRegister& regX) const
{
	armAsm->Ldr(a64::q7, dstIndirect);

	int offX = std::min(curCycle, 3);
	armMoveAddressToReg(RXVIXLSCRATCH, nVifMask);
	armAsm->Ldr(a64::q29, a64::MemOperand(RXVIXLSCRATCH, reinterpret_cast<const u8*>(nVifMask[0][offX]) - reinterpret_cast<const u8*>(nVifMask)));
	armAsm->Ldr(a64::q30, a64::MemOperand(RXVIXLSCRATCH, reinterpret_cast<const u8*>(nVifMask[1][offX]) - reinterpret_cast<const u8*>(nVifMask)));
	armAsm->Ldr(a64::q31, a64::MemOperand(RXVIXLSCRATCH, reinterpret_cast<const u8*>(nVifMask[2][offX]) - reinterpret_cast<const u8*>(nVifMask)));
	armAsm->And(regX.V16B(), regX.V16B(), a64::q29.V16B());
	armAsm->And(a64::q7.V16B(), a64::q7.V16B(), a64::q30.V16B());
	armAsm->Orr(regX.V16B(), regX.V16B(), a64::q31.V16B());
	armAsm->Orr(regX.V16B(), regX.V16B(), a64::q7.V16B());
	armAsm->Str(regX, dstIndirect);
}

// ecx = dest, edx = src
static void nVifGen(int usn, int mask, int curCycle)
{

	int usnpart = usn * 2 * 16;
	int maskpart = mask * 16;

	VifUnpackNEON_Simple vpugen(!!usn, !!mask, curCycle);

	for (int i = 0; i < 16; ++i)
	{
		nVifCall& ucall(nVifUpk[((usnpart + maskpart + i) * 4) + curCycle]);
		ucall = NULL;
		if (nVifT[i] == 0)
			continue;

		ucall = (nVifCall)armStartBlock();
		vpugen.xUnpack(i);
		vpugen.xMovDest();
		armAsm->Ret();
		armEndBlock();
	}
}

// Intrinsics-only path for ARM64
// Build a static table of function pointers that perform the unpack using NEON intrinsics
// instead of generating code via Vixl. These functions are used by the interpreter path
// (doMode == 0) and honor VIF masking via nVifMask.
namespace {
#ifdef __ARM_NEON
    static inline uint32x4_t make_u32x4(uint32_t a, uint32_t b, uint32_t c, uint32_t d)
    {
        uint32x2_t lo = vdup_n_u32(a);
        lo = vset_lane_u32(b, lo, 1);
        uint32x2_t hi = vdup_n_u32(c);
        hi = vset_lane_u32(d, hi, 1);
        return vcombine_u32(lo, hi);
    }
    // Masked store helper: (new & m0) | (old & m1) | m2
    static inline void mask_store_u32x4(uint32_t* dest, uint32x4_t v, int curCycle)
    {
        const uint32x4_t m0 = vld1q_u32(reinterpret_cast<const uint32_t*>(&nVifMask[0][curCycle][0]));
        const uint32x4_t m1 = vld1q_u32(reinterpret_cast<const uint32_t*>(&nVifMask[1][curCycle][0]));
        const uint32x4_t m2 = vld1q_u32(reinterpret_cast<const uint32_t*>(&nVifMask[2][curCycle][0]));
        const uint32x4_t old = vld1q_u32(dest);
        const uint32x4_t a = vandq_u32(v, m0);
        const uint32x4_t b = vandq_u32(old, m1);
        const uint32x4_t r = vorrq_u32(vorrq_u32(a, b), m2);
        vst1q_u32(dest, r);
    }

    // Sign/zero extend helpers
    static inline uint32_t zx8(uint8_t x) { return static_cast<uint32_t>(x); }
    static inline uint32_t sx8(uint8_t x) { return static_cast<uint32_t>(static_cast<int32_t>(static_cast<int8_t>(x))); }
    static inline uint32_t zx16(uint16_t x){ return static_cast<uint32_t>(x); }
    static inline uint32_t sx16(uint16_t x){ return static_cast<uint32_t>(static_cast<int32_t>(static_cast<int16_t>(x))); }

    template<bool USN, bool MASK, int CYCLE>
    static inline void store_u32x4(uint32_t* dest, uint32x4_t v)
    {
        if constexpr (MASK)
            mask_store_u32x4(dest, v, CYCLE);
        else
            vst1q_u32(dest, v);
    }

    template<bool USN> static inline uint32_t ext8(uint8_t x)
    {
        if constexpr (USN) return zx8(x); else return sx8(x);
    }
    template<bool USN> static inline uint32_t ext16(uint16_t x)
    {
        if constexpr (USN) return zx16(x); else return sx16(x);
    }

    template<bool USN, bool MASK, int CYCLE, int TYPE>
    static u32 unpack_intrinsics(void* dstp, const void* srcp)
    {
        uint32_t* dest = reinterpret_cast<uint32_t*>(dstp);
        uint32x4_t v;
        if constexpr (TYPE == 0) // S-32
        {
            const uint32_t s = *reinterpret_cast<const uint32_t*>(srcp);
            v = vdupq_n_u32(s);
        }
        else if constexpr (TYPE == 1) // S-16
        {
            const uint16_t s = *reinterpret_cast<const uint16_t*>(srcp);
            const uint32_t e = ext16<USN>(s);
            v = vdupq_n_u32(e);
        }
        else if constexpr (TYPE == 2) // S-8
        {
            const uint8_t s = *reinterpret_cast<const uint8_t*>(srcp);
            const uint32_t e = ext8<USN>(s);
            v = vdupq_n_u32(e);
        }
        else if constexpr (TYPE == 4) // V2-32
        {
            const uint32_t s0 = reinterpret_cast<const uint32_t*>(srcp)[0];
            const uint32_t s1 = reinterpret_cast<const uint32_t*>(srcp)[1];
            uint32x2_t a = vdup_n_u32(s0);
            a = vset_lane_u32(s1, a, 1);
            v = vcombine_u32(a, a);
        }
        else if constexpr (TYPE == 5) // V2-16
        {
            const uint16_t s0 = reinterpret_cast<const uint16_t*>(srcp)[0];
            const uint16_t s1 = reinterpret_cast<const uint16_t*>(srcp)[1];
            const uint32_t e0 = ext16<USN>(s0);
            const uint32_t e1 = ext16<USN>(s1);
            uint32x2_t a = vdup_n_u32(e0);
            a = vset_lane_u32(e1, a, 1);
            v = vcombine_u32(a, a);
        }
        else if constexpr (TYPE == 6) // V2-8
        {
            const uint8_t s0 = reinterpret_cast<const uint8_t*>(srcp)[0];
            const uint8_t s1 = reinterpret_cast<const uint8_t*>(srcp)[1];
            const uint32_t e0 = ext8<USN>(s0);
            const uint32_t e1 = ext8<USN>(s1);
            uint32x2_t a = vdup_n_u32(e0);
            a = vset_lane_u32(e1, a, 1);
            v = vcombine_u32(a, a);
        }
        else if constexpr (TYPE == 8) // V3-32 (uses V4 behavior)
        {
            const uint32_t s0 = reinterpret_cast<const uint32_t*>(srcp)[0];
            const uint32_t s1 = reinterpret_cast<const uint32_t*>(srcp)[1];
            const uint32_t s2 = reinterpret_cast<const uint32_t*>(srcp)[2];
            const uint32_t s3 = reinterpret_cast<const uint32_t*>(srcp)[3];
            v = make_u32x4(s0, s1, s2, s3);
        }
        else if constexpr (TYPE == 9) // V3-16 (uses V4 behavior)
        {
            const uint16_t s0 = reinterpret_cast<const uint16_t*>(srcp)[0];
            const uint16_t s1 = reinterpret_cast<const uint16_t*>(srcp)[1];
            const uint16_t s2 = reinterpret_cast<const uint16_t*>(srcp)[2];
            const uint16_t s3 = reinterpret_cast<const uint16_t*>(srcp)[3];
            const uint32_t e0 = ext16<USN>(s0);
            const uint32_t e1 = ext16<USN>(s1);
            const uint32_t e2 = ext16<USN>(s2);
            const uint32_t e3 = ext16<USN>(s3);
            v = make_u32x4(e0, e1, e2, e3);
        }
        else if constexpr (TYPE == 10) // V3-8 (uses V4 behavior)
        {
            const uint8_t s0 = reinterpret_cast<const uint8_t*>(srcp)[0];
            const uint8_t s1 = reinterpret_cast<const uint8_t*>(srcp)[1];
            const uint8_t s2 = reinterpret_cast<const uint8_t*>(srcp)[2];
            const uint8_t s3 = reinterpret_cast<const uint8_t*>(srcp)[3];
            const uint32_t e0 = ext8<USN>(s0);
            const uint32_t e1 = ext8<USN>(s1);
            const uint32_t e2 = ext8<USN>(s2);
            const uint32_t e3 = ext8<USN>(s3);
            v = make_u32x4(e0, e1, e2, e3);
        }
        else if constexpr (TYPE == 12) // V4-32
        {
            uint32x4_t tmp = vld1q_u32(reinterpret_cast<const uint32_t*>(srcp));
            v = tmp;
        }
        else if constexpr (TYPE == 13) // V4-16
        {
            const uint16_t* s = reinterpret_cast<const uint16_t*>(srcp);
            const uint32_t e0 = ext16<USN>(s[0]);
            const uint32_t e1 = ext16<USN>(s[1]);
            const uint32_t e2 = ext16<USN>(s[2]);
            const uint32_t e3 = ext16<USN>(s[3]);
            v = make_u32x4(e0, e1, e2, e3);
        }
        else if constexpr (TYPE == 14) // V4-8
        {
            const uint8_t* s = reinterpret_cast<const uint8_t*>(srcp);
            const uint32_t e0 = ext8<USN>(s[0]);
            const uint32_t e1 = ext8<USN>(s[1]);
            const uint32_t e2 = ext8<USN>(s[2]);
            const uint32_t e3 = ext8<USN>(s[3]);
            v = make_u32x4(e0, e1, e2, e3);
        }
        else if constexpr (TYPE == 15) // V4-5
        {
            const uint32_t data = *reinterpret_cast<const uint32_t*>(srcp);
            const uint32_t x = (data & 0x001f) << 3;
            const uint32_t y = (data & 0x03e0) >> 2;
            const uint32_t z = (data & 0x7c00) >> 7;
            const uint32_t w = (data & 0x8000) >> 8;
            uint32x4_t t = make_u32x4(x, y, z, w);
            // Mask to 8 bits like SSE path (shift left then right by 24)
            t = vshlq_n_u32(t, 24);
            v = vshrq_n_u32(t, 24);
        }
        else
        {
            // Unused/invalid types should never be called; default to zero.
            v = vdupq_n_u32(0);
        }

        store_u32x4<USN, MASK, CYCLE>(dest, v);
        return 0;
    }

    template<bool USN, bool MASK, int CYCLE>
    static void assign_type(int type, nVifCall& fp)
    {
        switch (type)
        {
            case 0:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 0>;  break;
            case 1:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 1>;  break;
            case 2:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 2>;  break;
            case 4:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 4>;  break;
            case 5:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 5>;  break;
            case 6:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 6>;  break;
            case 8:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 8>;  break;
            case 9:  fp = &unpack_intrinsics<USN, MASK, CYCLE, 9>;  break;
            case 10: fp = &unpack_intrinsics<USN, MASK, CYCLE, 10>; break;
            case 12: fp = &unpack_intrinsics<USN, MASK, CYCLE, 12>; break;
            case 13: fp = &unpack_intrinsics<USN, MASK, CYCLE, 13>; break;
            case 14: fp = &unpack_intrinsics<USN, MASK, CYCLE, 14>; break;
            case 15: fp = &unpack_intrinsics<USN, MASK, CYCLE, 15>; break;
            default: fp = nullptr; break; // 3,7,11 invalid
        }
    }
#endif // __ARM_NEON
} // anonymous namespace

void VifUnpackSSE_Init()
{
#ifdef __ARM_NEON
    DevCon.WriteLn("Initializing ARM64 NEON intrinsics VIF unpack functions...");

    for (int usn = 0; usn < 2; usn++)
    {
        for (int mask = 0; mask < 2; mask++)
        {
            for (int cycle = 0; cycle < 4; cycle++)
            {
                for (int type = 0; type < 16; type++)
                {
                    nVifCall& slot = nVifUpk[(((usn * 2 * 16) + (mask * 16) + type) * 4) + cycle];
                    slot = nullptr;
                    if (nVifT[type] == 0)
                        continue;

                    if (usn)
                    {
                        if (mask)
                            assign_type<true, true, 0>(type, slot);
                        else
                            assign_type<true, false, 0>(type, slot);
                    }
                    else
                    {
                        if (mask)
                            assign_type<false, true, 0>(type, slot);
                        else
                            assign_type<false, false, 0>(type, slot);
                    }

                    // Cycle selection only affects masking (which we handle inside mask_store),
                    // so we need cycle-specific instantiations. The assign_type above uses CYCLE=0,
                    // but masking reads the provided cycle. To maintain the existing table layout,
                    // create 4 identical entries differing only by the cycle value.
                    // Reassign the function pointer with the correct CYCLE template param:
                    if (slot)
                    {
                        if (usn)
                        {
                            if (mask)
                            {
                                switch (cycle)
                                {
                                    case 0: assign_type<true,  true,  0>(type, slot); break;
                                    case 1: assign_type<true,  true,  1>(type, slot); break;
                                    case 2: assign_type<true,  true,  2>(type, slot); break;
                                    case 3: assign_type<true,  true,  3>(type, slot); break;
                                }
                            }
                            else
                            {
                                switch (cycle)
                                {
                                    case 0: assign_type<true,  false, 0>(type, slot); break;
                                    case 1: assign_type<true,  false, 1>(type, slot); break;
                                    case 2: assign_type<true,  false, 2>(type, slot); break;
                                    case 3: assign_type<true,  false, 3>(type, slot); break;
                                }
                            }
                        }
                        else
                        {
                            if (mask)
                            {
                                switch (cycle)
                                {
                                    case 0: assign_type<false, true,  0>(type, slot); break;
                                    case 1: assign_type<false, true,  1>(type, slot); break;
                                    case 2: assign_type<false, true,  2>(type, slot); break;
                                    case 3: assign_type<false, true,  3>(type, slot); break;
                                }
                            }
                            else
                            {
                                switch (cycle)
                                {
                                    case 0: assign_type<false, false, 0>(type, slot); break;
                                    case 1: assign_type<false, false, 1>(type, slot); break;
                                    case 2: assign_type<false, false, 2>(type, slot); break;
                                    case 3: assign_type<false, false, 3>(type, slot); break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DevCon.WriteLn("Initialized %zu intrinsics unpack entries", static_cast<size_t>(2 * 2 * 16 * 4));
#else
    // Fallback to existing JIT path when not building for ARM NEON.
    DevCon.WriteLn("ARM NEON not available; using Vixl JIT path for VIF unpack.");
    HostSys::BeginCodeWrite();
    armSetAsmPtr(SysMemory::GetVIFUnpackRec(), SysMemory::GetVIFUnpackRecEnd() - SysMemory::GetVIFUnpackRec(), nullptr);
    for (int a = 0; a < 2; a++)
        for (int b = 0; b < 2; b++)
            for (int c = 0; c < 4; c++)
                nVifGen(a, b, c);
    Perf::any.Register(SysMemory::GetVIFUnpackRec(), armGetAsmPtr() - SysMemory::GetVIFUnpackRec(), "VIF Unpack");
    HostSys::EndCodeWrite();
#endif
}

#ifdef __ARM_NEON
// --------------------------------------------------------------------------------------------------
//  NEON helper function implementations
//
//  These functions provide standalone implementations of the basic unpacking
//  operations used by the dynamic recompiler.  They can be called directly
//  from C++ code to perform loads and widening using NEON intrinsics.  See
//  Vif_UnpackNEON.h for the declarations.

/// Load a packed 8‑bit value from src, widen to 32 bits and convert to float.
float32x4_t pmovxx8_neon(const u8* src, bool usn)
{
    // Load 8 bytes from memory.  We only need the lower 4 elements but vld1_u8
    // always reads 8 bytes, which is safe as long as src points to at least 4
    // bytes of valid data.
    uint8x8_t v8 = vld1_u8(reinterpret_cast<const uint8_t*>(src));
    if (usn)
    {
        uint16x8_t v16 = vmovl_u8(v8);
        uint16x4_t v16low = vget_low_u16(v16);
        uint32x4_t v32 = vmovl_u16(v16low);
        return vcvtq_f32_u32(v32);
    }
    else
    {
        int8x8_t s8 = vreinterpret_s8_u8(v8);
        int16x8_t v16 = vmovl_s8(s8);
        int16x4_t v16low = vget_low_s16(v16);
        int32x4_t v32 = vmovl_s16(v16low);
        return vcvtq_f32_s32(v32);
    }
}

/// Load a packed 16‑bit value from src, widen to 32 bits and convert to float.
float32x4_t pmovxx16_neon(const u16* src, bool usn)
{
    uint16x4_t v16 = vld1_u16(reinterpret_cast<const uint16_t*>(src));
    if (usn)
    {
        uint32x4_t v32 = vmovl_u16(v16);
        return vcvtq_f32_u32(v32);
    }
    else
    {
        int16x4_t s16 = vreinterpret_s16_u16(v16);
        int32x4_t v32 = vmovl_s16(s16);
        return vcvtq_f32_s32(v32);
    }
}
#endif // __ARM_NEON
