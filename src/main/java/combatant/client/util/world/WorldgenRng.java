/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.world;

/**
 * High-performance, self-contained world generation random number generator.
 * <p>
 * Supports both modern vanilla 1.18+ Xoroshiro128++ algorithms and legacy 48-bit
 * Linear Congruential Generator (LCG) algorithms. Fully self-contained without
 * dependencies on server-only levelgen classes.
 */
public abstract class WorldgenRng {

    /**
     * Sets the internal seed of the RNG.
     *
     * @param seed the seed to set
     */
    public abstract void setSeed(long seed);

    /**
     * Next pseudo-random integer in the range [0, bound).
     *
     * @param bound upper bound (exclusive), must be positive
     * @return pseudo-random integer
     */
    public abstract int nextInt(int bound);

    /**
     * Next pseudo-random 64-bit long integer.
     *
     * @return pseudo-random long
     */
    public abstract long nextLong();

    /**
     * Next pseudo-random float in the range [0.0, 1.0).
     *
     * @return pseudo-random float
     */
    public abstract float nextFloat();

    /**
     * Next pseudo-random double in the range [0.0, 1.0).
     *
     * @return pseudo-random double
     */
    public abstract double nextDouble();

    /**
     * Next pseudo-random boolean value.
     *
     * @return pseudo-random boolean
     */
    public abstract boolean nextBoolean();

    /**
     * Initializes the RNG for chunk decoration and returns the decoration seed.
     *
     * @param worldSeed the world seed
     * @param blockX    the block X coordinate (typically chunkX &lt;&lt; 4)
     * @param blockZ    the block Z coordinate (typically chunkZ &lt;&lt; 4)
     * @return the computed decoration seed
     */
    public abstract long setDecorationSeed(long worldSeed, int blockX, int blockZ);

    /**
     * Seeds the RNG for placing an individual feature within a step.
     *
     * @param decorationSeed the decoration seed computed from {@link #setDecorationSeed}
     * @param featureIndex   the index of the feature
     * @param step           the decoration step index
     */
    public abstract void setFeatureSeed(long decorationSeed, int featureIndex, int step);

    /**
     * Seeds the RNG for large feature placement (e.g. structures) in a chunk.
     *
     * @param worldSeed the world seed
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     */
    public abstract void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ);

    /**
     * Seeds the RNG for large feature placement with a salt.
     *
     * @param worldSeed the world seed
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     * @param salt      salt value
     */
    public abstract void setLargeFeatureWithSalt(long worldSeed, int chunkX, int chunkZ, int salt);

    /**
     * Seeds the RNG for testing or populating a slime chunk.
     *
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     * @param worldSeed world seed
     * @param salt      salt value (vanilla default: 987234911L)
     */
    public abstract void seedSlimeChunk(int chunkX, int chunkZ, long worldSeed, long salt);

    /**
     * Creates a modern Xoroshiro128++ worldgen RNG initialized with the given seed.
     *
     * @param seed the seed
     * @return modern WorldgenRng instance
     */
    public static WorldgenRng create(long seed) {
        return new XoroshiroWorldgenRng(seed);
    }

    /**
     * Creates a legacy 48-bit LCG worldgen RNG initialized with the given seed.
     *
     * @param seed the seed
     * @return legacy WorldgenRng instance
     */
    public static WorldgenRng legacy(long seed) {
        return new LegacyWorldgenRng(seed);
    }

    /**
     * Legacy 48-bit Linear Congruential Generator implementation.
     */
    public static final class LegacyWorldgenRng extends WorldgenRng {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;
        private static final double DOUBLE_UNIT = 1.0 / (1L << 53);
        private static final float FLOAT_UNIT = 1.0f / (1 << 24);

        private long seed;

        public LegacyWorldgenRng(long seed) {
            setSeed(seed);
        }

        @Override
        public void setSeed(long seed) {
            this.seed = (seed ^ MULTIPLIER) & MASK;
        }

        private int next(int bits) {
            this.seed = (this.seed * MULTIPLIER + ADDEND) & MASK;
            return (int) (this.seed >>> (48 - bits));
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            if ((bound & -bound) == bound) {
                return (int) ((bound * (long) next(31)) >> 31);
            }
            int bits, val;
            do {
                bits = next(31);
                val = bits % bound;
            } while (bits - val + (bound - 1) < 0);
            return val;
        }

        @Override
        public long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }

        @Override
        public float nextFloat() {
            return next(24) * FLOAT_UNIT;
        }

        @Override
        public double nextDouble() {
            return (((long) next(26) << 27) + next(27)) * DOUBLE_UNIT;
        }

        @Override
        public boolean nextBoolean() {
            return next(1) != 0;
        }

        @Override
        public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
            setSeed(worldSeed);
            long a = nextLong() | 1L;
            long b = nextLong() | 1L;
            long decorationSeed = ((long) blockX * a + (long) blockZ * b) ^ worldSeed;
            setSeed(decorationSeed);
            return decorationSeed;
        }

        @Override
        public void setFeatureSeed(long decorationSeed, int featureIndex, int step) {
            long featureSeed = decorationSeed + (long) featureIndex + (10000L * (long) step);
            setSeed(featureSeed);
        }

        @Override
        public void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
            setSeed(worldSeed);
            long a = nextLong();
            long b = nextLong();
            long featureSeed = ((long) chunkX * a ^ (long) chunkZ * b) ^ worldSeed;
            setSeed(featureSeed);
        }

        @Override
        public void setLargeFeatureWithSalt(long worldSeed, int chunkX, int chunkZ, int salt) {
            long featureSeed = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + worldSeed + (long) salt;
            setSeed(featureSeed);
        }

        @Override
        public void seedSlimeChunk(int chunkX, int chunkZ, long worldSeed, long salt) {
            long slimeSeed = worldSeed
                    + (long) (chunkX * chunkX * 4987142)
                    + (long) (chunkX * 5947611)
                    + (long) (chunkZ * chunkZ) * 4392871L
                    + (long) (chunkZ * 389711) ^ salt;
            setSeed(slimeSeed);
        }
    }

    /**
     * Modern Xoroshiro128++ implementation matching Minecraft 1.18+ worldgen.
     */
    public static final class XoroshiroWorldgenRng extends WorldgenRng {
        private static final double DOUBLE_UNIT = 0x1.0p-53; // 1.0 / (1L << 53)
        private static final float FLOAT_UNIT = 0x1.0p-24f; // 1.0f / (1 << 24)

        private static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;
        private static final long GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15L;

        private long seedLo;
        private long seedHi;

        public XoroshiroWorldgenRng(long seed) {
            setSeed(seed);
        }

        public XoroshiroWorldgenRng(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
            if ((this.seedLo | this.seedHi) == 0L) {
                this.seedLo = -7046029254386353131L;
                this.seedHi = 7640891576956012809L; // 0x6A09E667F3BCC909L
            }
        }

        @Override
        public void setSeed(long seed) {
            // SplitMix64 initialization of state
            long l0 = seed ^ 0x6A09E667F3BCC909L;
            long l1 = l0 + 0x9E3779B97F4A7C15L;
            this.seedLo = staffordMix13(l0);
            this.seedHi = staffordMix13(l1);
            if ((this.seedLo | this.seedHi) == 0L) {
                this.seedLo = -7046029254386353131L;
                this.seedHi = 7640891576956012809L; // 0x6A09E667F3BCC909L
            }
        }

        private static long staffordMix13(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        @Override
        public long nextLong() {
            long s0 = this.seedLo;
            long s1 = this.seedHi;
            long result = Long.rotateLeft(s0 + s1, 17) + s0;

            s1 ^= s0;
            this.seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
            this.seedHi = Long.rotateLeft(s1, 28);

            return result;
        }

        private long nextBits(int bits) {
            return nextLong() >>> (64 - bits);
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            long r = Integer.toUnsignedLong((int) nextLong());
            long m = r * (long) bound;
            long l = m & 0xFFFFFFFFL;
            if (l < (long) bound) {
                int t = Integer.remainderUnsigned(~bound + 1, bound);
                while (l < Integer.toUnsignedLong(t)) {
                    r = Integer.toUnsignedLong((int) nextLong());
                    m = r * (long) bound;
                    l = m & 0xFFFFFFFFL;
                }
            }
            return (int) (m >>> 32);
        }

        @Override
        public float nextFloat() {
            return (float) nextBits(24) * FLOAT_UNIT;
        }

        @Override
        public double nextDouble() {
            return (double) nextBits(53) * DOUBLE_UNIT;
        }

        @Override
        public boolean nextBoolean() {
            return (nextLong() & 1L) != 0;
        }

        @Override
        public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
            setSeed(worldSeed);
            long a = nextLong() | 1L;
            long b = nextLong() | 1L;
            long decorationSeed = ((long) blockX * a + (long) blockZ * b) ^ worldSeed;
            setSeed(decorationSeed);
            return decorationSeed;
        }

        @Override
        public void setFeatureSeed(long decorationSeed, int featureIndex, int step) {
            long featureSeed = decorationSeed + (long) featureIndex + (10000L * (long) step);
            setSeed(featureSeed);
        }

        @Override
        public void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
            setSeed(worldSeed);
            long a = nextLong();
            long b = nextLong();
            long featureSeed = ((long) chunkX * a ^ (long) chunkZ * b) ^ worldSeed;
            setSeed(featureSeed);
        }

        @Override
        public void setLargeFeatureWithSalt(long worldSeed, int chunkX, int chunkZ, int salt) {
            long featureSeed = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + worldSeed + (long) salt;
            setSeed(featureSeed);
        }

        @Override
        public void seedSlimeChunk(int chunkX, int chunkZ, long worldSeed, long salt) {
            long slimeSeed = worldSeed
                    + (long) (chunkX * chunkX * 4987142)
                    + (long) (chunkX * 5947611)
                    + (long) (chunkZ * chunkZ) * 4392871L
                    + (long) (chunkZ * 389711) ^ salt;
            setSeed(slimeSeed);
        }
    }
}
