package com.example.util

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code encoder (Byte mode, ECC Level L, Versions 1..10)
 * for generating scannable UPI payment QR matrices locally without external dependencies.
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val blocks: Int,
        val alignmentCenters: IntArray
    )

    // Level L capacity table for Versions 1..10 (up to 271 bytes - ample for upi://pay URIs)
    private val specs = listOf(
        VersionSpec(1, 21, 26, 7, 1, intArrayOf()),
        VersionSpec(2, 25, 44, 10, 1, intArrayOf(6, 18)),
        VersionSpec(3, 29, 70, 15, 1, intArrayOf(6, 22)),
        VersionSpec(4, 33, 100, 20, 1, intArrayOf(6, 26)),
        VersionSpec(5, 37, 134, 26, 1, intArrayOf(6, 30)),
        VersionSpec(6, 41, 172, 18, 2, intArrayOf(6, 34)),
        VersionSpec(7, 45, 196, 20, 2, intArrayOf(6, 22, 38)),
        VersionSpec(8, 49, 242, 24, 2, intArrayOf(6, 24, 42)),
        VersionSpec(9, 53, 292, 30, 2, intArrayOf(6, 26, 46)),
        VersionSpec(10, 57, 346, 18, 4, intArrayOf(6, 28, 50))
    )

    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val factor = gfExp[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], factor)
            }
            poly = next
        }
        return poly
    }

    private fun rsComputeRemainder(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGeneratorPoly(ecLen)
        val remainder = IntArray(ecLen)
        for (b in data) {
            val factor = b xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecLen - 1)
            remainder[ecLen - 1] = 0
            for (i in 0 until ecLen) {
                remainder[i] = remainder[i] xor gfMul(gen[i + 1], factor)
            }
        }
        return remainder
    }

    fun encode(text: String): Array<BooleanArray>? {
        val payload = text.toByteArray(Charsets.UTF_8)
        val spec = specs.firstOrNull { s ->
            val dataCodewords = s.totalCodewords - s.ecCodewordsPerBlock * s.blocks
            val charCountBits = if (s.version <= 9) 8 else 16
            val neededBits = 4 + charCountBits + payload.size * 8
            neededBits <= dataCodewords * 8
        } ?: return null

        val dataCodewordsCount = spec.totalCodewords - spec.ecCodewordsPerBlock * spec.blocks
        val bits = ArrayList<Int>(dataCodewordsCount * 8)

        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Mode indicator: Byte (0100)
        appendBits(0b0100, 4)
        // Character count indicator
        appendBits(payload.size, if (spec.version <= 9) 8 else 16)
        // Payload bytes
        for (byte in payload) {
            appendBits(byte.toInt() and 0xFF, 8)
        }
        // Terminator up to 4 zeros
        val maxBits = dataCodewordsCount * 8
        val terminator = minOf(4, maxBits - bits.size)
        repeat(terminator) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val dataBytes = IntArray(dataCodewordsCount)
        var byteIdx = 0
        for (i in 0 until bits.size step 8) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[i + b]
            }
            dataBytes[byteIdx++] = v
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (byteIdx < dataCodewordsCount) {
            dataBytes[byteIdx++] = padBytes[padIdx % 2]
            padIdx++
        }

        // Split into blocks & compute RS ECC
        val numBlocks = spec.blocks
        val shortBlockLen = dataCodewordsCount / numBlocks
        val numLongBlocks = dataCodewordsCount % numBlocks
        val dataBlocks = ArrayList<IntArray>(numBlocks)
        val ecBlocks = ArrayList<IntArray>(numBlocks)
        var offset = 0
        for (b in 0 until numBlocks) {
            val len = shortBlockLen + if (b >= numBlocks - numLongBlocks) 1 else 0
            val block = dataBytes.copyOfRange(offset, offset + len)
            offset += len
            dataBlocks.add(block)
            ecBlocks.add(rsComputeRemainder(block, spec.ecCodewordsPerBlock))
        }

        // Interleave data and EC codewords
        val finalCodewords = ArrayList<Int>(spec.totalCodewords)
        val maxDataBlockLen = shortBlockLen + if (numLongBlocks > 0) 1 else 0
        for (i in 0 until maxDataBlockLen) {
            for (block in dataBlocks) {
                if (i < block.size) finalCodewords.add(block[i])
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (ec in ecBlocks) {
                finalCodewords.add(ec[i])
            }
        }

        val size = spec.size
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun setFunctionModule(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = dark
                isFunction[y][x] = true
            }
        }

        // Finder patterns + separators
        fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    val dark = dist != 2 && dist != 4
                    setFunctionModule(cx + dx, cy + dy, dark)
                }
            }
        }
        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)

        // Timing patterns
        for (i in 0 until size) {
            if (!isFunction[6][i]) setFunctionModule(i, 6, i % 2 == 0)
            if (!isFunction[i][6]) setFunctionModule(6, i, i % 2 == 0)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (cy in centers) {
            for (cx in centers) {
                if (isFunction[cy][cx]) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        setFunctionModule(cx + dx, cy + dy, dist != 1)
                    }
                }
            }
        }

        // Reserve format info + dark module
        for (i in 0..8) {
            if (i != 6) {
                setFunctionModule(8, i, false)
                setFunctionModule(i, 8, false)
            }
        }
        for (i in 0..7) {
            setFunctionModule(size - 1 - i, 8, false)
            setFunctionModule(8, size - 1 - i, false)
        }
        setFunctionModule(8, size - 8, true)

        // Reserve version info for Version >= 7
        if (spec.version >= 7) {
            var rem = spec.version
            for (i in 0 until 12) {
                rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            }
            val versionBits = (spec.version shl 12) or rem
            for (i in 0 until 18) {
                val bit = ((versionBits ushr i) and 1) != 0
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunctionModule(a, b, bit)
                setFunctionModule(b, a, bit)
            }
        }

        // Place data bits in zigzag order
        val allBits = BooleanArray(finalCodewords.size * 8)
        var bitPtr = 0
        for (cw in finalCodewords) {
            for (b in 7 downTo 0) {
                allBits[bitPtr++] = ((cw ushr b) and 1) != 0
            }
        }

        var idx = 0
        var upward = true
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until size) {
                val y = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val x = right - j
                    if (!isFunction[y][x]) {
                        val dark = if (idx < allBits.size) allBits[idx++] else false
                        modules[y][x] = dark
                    }
                }
            }
            upward = !upward
            right -= 2
        }

        // Apply Mask 0 ((x + y) % 2 == 0) and write format info for ECC Level L (01), Mask 0 (000)
        // Format data = 0b01000 = 8 -> BCH encoded + XOR 0x5412 = 0x77C4
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y][x] && ((x + y) % 2 == 0)) {
                    modules[y][x] = !modules[y][x]
                }
            }
        }

        val formatBits = 0x77C4 // Level L (01), Mask 0 (000)
        for (i in 0..5) setFunctionModule(8, i, ((formatBits ushr i) and 1) != 0)
        setFunctionModule(8, 7, ((formatBits ushr 6) and 1) != 0)
        setFunctionModule(8, 8, ((formatBits ushr 7) and 1) != 0)
        setFunctionModule(7, 8, ((formatBits ushr 8) and 1) != 0)
        for (i in 9..14) setFunctionModule(14 - i, 8, ((formatBits ushr i) and 1) != 0)

        for (i in 0..7) setFunctionModule(size - 1 - i, 8, ((formatBits ushr i) and 1) != 0)
        for (i in 8..14) setFunctionModule(8, size - 15 + i, ((formatBits ushr i) and 1) != 0)
        setFunctionModule(8, size - 8, true)

        return modules
    }
}
