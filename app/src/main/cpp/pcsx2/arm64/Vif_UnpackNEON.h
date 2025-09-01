// SPDX-FileCopyrightText: 2002-2025 PCSX2 Dev Team
// SPDX-License-Identifier: GPL-3.0

#pragma once

#include "Common.h"
#include "Vif_Dma.h"
#include "Vif_Dynarec.h"
#include "VixlHelpers.h"

#define xmmCol0 vixl::aarch64::q2
#define xmmCol1 vixl::aarch64::q3
#define xmmCol2 vixl::aarch64::q4
#define xmmCol3 vixl::aarch64::q5
#define xmmRow vixl::aarch64::q6
#define xmmTemp vixl::aarch64::q7

// --------------------------------------------------------------------------------------
//  VifUnpackSSE_Base
// --------------------------------------------------------------------------------------
class VifUnpackNEON_Base
{
public:
	bool usn; // unsigned flag
	bool doMask; // masking write enable flag
	int UnpkLoopIteration;
	int UnpkNoOfIterations;
	int IsAligned;


protected:
	vixl::aarch64::MemOperand dstIndirect;
	vixl::aarch64::MemOperand srcIndirect;
	vixl::aarch64::VRegister workReg;
	vixl::aarch64::VRegister destReg;
	vixl::aarch64::WRegister workGprW;

public:
	VifUnpackNEON_Base();
	virtual ~VifUnpackNEON_Base() = default;

	virtual void xUnpack(int upktype) const;
	virtual bool IsWriteProtectedOp() const = 0;
	virtual bool IsInputMasked() const = 0;
	virtual bool IsUnmaskedOp() const = 0;
	virtual void xMovDest() const;

protected:
	virtual void doMaskWrite(const vixl::aarch64::VRegister& regX) const = 0;

	virtual void xShiftR(const vixl::aarch64::VRegister& regX, int n) const;
	virtual void xPMOVXX8(const vixl::aarch64::VRegister& regX) const;
	virtual void xPMOVXX16(const vixl::aarch64::VRegister& regX) const;

	virtual void xUPK_S_32() const;
	virtual void xUPK_S_16() const;
	virtual void xUPK_S_8() const;

	virtual void xUPK_V2_32() const;
	virtual void xUPK_V2_16() const;
	virtual void xUPK_V2_8() const;

	virtual void xUPK_V3_32() const;
	virtual void xUPK_V3_16() const;
	virtual void xUPK_V3_8() const;

	virtual void xUPK_V4_32() const;
	virtual void xUPK_V4_16() const;
	virtual void xUPK_V4_8() const;
	virtual void xUPK_V4_5() const;
};

// --------------------------------------------------------------------------------------
//  VifUnpackSSE_Simple
// --------------------------------------------------------------------------------------
class VifUnpackNEON_Simple : public VifUnpackNEON_Base
{
	typedef VifUnpackNEON_Base _parent;

public:
	int curCycle;

public:
	VifUnpackNEON_Simple(bool usn_, bool domask_, int curCycle_);
	virtual ~VifUnpackNEON_Simple() = default;

	virtual bool IsWriteProtectedOp() const { return false; }
	virtual bool IsInputMasked() const { return false; }
	virtual bool IsUnmaskedOp() const { return !doMask; }

protected:
	virtual void doMaskWrite(const vixl::aarch64::VRegister& regX) const;
};

// --------------------------------------------------------------------------------------
//  VifUnpackSSE_Dynarec
// --------------------------------------------------------------------------------------
class VifUnpackNEON_Dynarec : public VifUnpackNEON_Base
{
	typedef VifUnpackNEON_Base _parent;

public:
	bool isFill;
	int doMode; // two bit value representing difference mode
	bool skipProcessing;
	bool inputMasked;

protected:
	const nVifStruct& v; // vif0 or vif1
	const nVifBlock& vB; // some pre-collected data from VifStruct
	int vCL; // internal copy of vif->cl

public:
	VifUnpackNEON_Dynarec(const nVifStruct& vif_, const nVifBlock& vifBlock_);
	VifUnpackNEON_Dynarec(const VifUnpackNEON_Dynarec& src) // copy constructor
		: _parent(src)
		, v(src.v)
		, vB(src.vB)
	{
		isFill = src.isFill;
		vCL = src.vCL;
	}

	virtual ~VifUnpackNEON_Dynarec() = default;

	virtual bool IsWriteProtectedOp() const { return skipProcessing; }
	virtual bool IsInputMasked() const { return inputMasked; }
	virtual bool IsUnmaskedOp() const { return !doMode && !doMask; }

	void ModUnpack(int upknum, bool PostOp);
	void ProcessMasks();
	void CompileRoutine();

protected:
	virtual void doMaskWrite(const vixl::aarch64::VRegister& regX) const;
	void SetMasks(int cS) const;
	void writeBackRow() const;

	static VifUnpackNEON_Dynarec FillingWrite(const VifUnpackNEON_Dynarec& src)
	{
		VifUnpackNEON_Dynarec fillingWrite(src);
		fillingWrite.doMask = true;
		fillingWrite.doMode = 0;
		return fillingWrite;
	}
};

// --------------------------------------------------------------------------------------------------
//  NEON helper functions
//
//  The dynamic recompiler versions of VIF unpacking operate by emitting ARM64 instructions via Vixl.
//  For better performance and maintainability on modern devices it can be advantageous to bypass
//  the macro assembler entirely and instead perform the unpacking directly with NEON intrinsics.
//  The declarations below expose a handful of helper routines which load packed VIF data,
//  widen it to 32‑bit values, and perform lane duplication.  These functions are implemented
//  in Vif_UnpackNEON.cpp and can be called from C++ code when appropriate.

#ifdef __ARM_NEON

#include <arm_neon.h>

/// \brief Load a packed 8‑bit value from @p src, widen it to a vector of four 32‑bit values
/// and convert them to float.  When @p usn is true the values are treated as unsigned,
/// otherwise they are treated as signed.
float32x4_t pmovxx8_neon(const u8* src, bool usn);

/// \brief Load a packed 16‑bit value from @p src, widen it to a vector of four 32‑bit values
/// and convert them to float.  When @p usn is true the values are treated as unsigned,
/// otherwise they are treated as signed.
float32x4_t pmovxx16_neon(const u16* src, bool usn);

/// \brief Duplicate a single 32‑bit lane from @p vec across all four lanes.  The lane index
/// must be in the range [0,3].
static inline float32x4_t dup_lane_f32(const float32x4_t vec, int lane)
{
    switch (lane & 3)
    {
        case 0: return vdupq_laneq_f32(vec, 0);
        case 1: return vdupq_laneq_f32(vec, 1);
        case 2: return vdupq_laneq_f32(vec, 2);
        default: return vdupq_laneq_f32(vec, 3);
    }
}

/// \brief Duplicate one half (low or high) of the 128‑bit vector @p vec across both halves.
/// If @p half is 0 the low half is duplicated; if 1 the high half is duplicated.  When
/// @p zero_last is true the final 32‑bit lane of the result is zeroed.
static inline float32x4_t dup_half_f32(const float32x4_t vec, int half, bool zero_last)
{
    float32x4_t result;
    if ((half & 1) == 0)
    {
        float32x2_t lo = vget_low_f32(vec);
        result = vcombine_f32(lo, lo);
    }
    else
    {
        float32x2_t hi = vget_high_f32(vec);
        result = vcombine_f32(hi, hi);
    }
    if (zero_last)
        result = vsetq_lane_f32(0.0f, result, 3);
    return result;
}

#endif // __ARM_NEON
