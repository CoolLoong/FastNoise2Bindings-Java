package com.github.fastnoise;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FastNoiseTest {
    static FastNoise maxSmooth;

    @BeforeAll
    public static void testFastNoise() throws IOException {
        FastNoise cellular = new FastNoise("CellularDistance");
        cellular.set("ReturnType", "Index0Add1");
        cellular.set("DistanceIndex0", 2);

        FastNoise fractal = new FastNoise("FractalFBm");
        fractal.set("Source", new FastNoise("Simplex"));
        fractal.set("Gain", 0.3f);
        fractal.set("Lacunarity", 0.6f);

        FastNoise addDim = new FastNoise("AddDimension");
        addDim.set("Source", cellular);
        addDim.set("NewDimensionPosition", 0.5f);

        maxSmooth = new FastNoise("MaxSmooth");
        maxSmooth.set("LHS", fractal);
        maxSmooth.set("RHS", addDim);
        System.out.println("SIMD Level " + maxSmooth.getSIMDLevel());
    }

    void gen(int size, FastNoise noiseS, String filename, BiConsumer<FastNoise, DataOutputStream> rw) {
        final int imageDataOffset = 14 + 12 + (256 * 3);

        try (DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filename + ".bmp")))) {
            // File header (14 bytes)
            writer.writeBytes("BM");
            writer.writeInt(Integer.reverseBytes(imageDataOffset + size * size)); // file size
            writer.writeInt(0); // reserved
            writer.writeInt(Integer.reverseBytes(imageDataOffset)); // image data offset

            // BMP Info Header (12 bytes)
            writer.writeInt(Integer.reverseBytes(12)); // size of header
            writer.writeShort(Short.reverseBytes((short) size)); // width
            writer.writeShort(Short.reverseBytes((short) size)); // height
            writer.writeShort(Short.reverseBytes((short) 1)); // color planes
            writer.writeShort(Short.reverseBytes((short) 8)); // bit depth

            // Color map (grayscale)
            for (int i = 0; i < 256; i++) {
                writer.writeByte(i); // blue
                writer.writeByte(i); // green
                writer.writeByte(i); // red
            }

            rw.accept(noiseS, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Created " + filename + ".bmp " + size + "x" + size);
    }

    final int tow_dimension_size = 512;
    BiConsumer<FastNoise, DataOutputStream> genTileable2D = (noiseS, writer) -> {
        // Image data
        final var noiseData = new FloatArray(tow_dimension_size * tow_dimension_size);
        FastNoise.OutputMinMax minMax = noiseS.genTileable2D(noiseData, tow_dimension_size, tow_dimension_size, 0.02f, 1337);

        float scale = 255.0f / (minMax.max - minMax.min);
        for (float noise : noiseData) {
            // Scale noise to 0 - 255
            int noiseI = Math.round((noise - minMax.min) * scale);
            try {
                writer.writeByte(Math.max(0, Math.min(255, noiseI)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    BiConsumer<FastNoise, DataOutputStream> genUniformGrid2D = (ns, writer) -> {
        // Image data
        final var noiseData = new FloatArray(tow_dimension_size * tow_dimension_size);
        FastNoise.OutputMinMax minMax = ns.genUniformGrid2D(noiseData, 0, 0, tow_dimension_size, tow_dimension_size, 0.02f, 1337);

        float scale = 255.0f / (minMax.max - minMax.min);
        for (float noise : noiseData) {
            // Scale noise to 0 - 255
            int noiseI = Math.round((noise - minMax.min) * scale);
            try {
                writer.writeByte(Math.max(0, Math.min(255, noiseI)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    BiConsumer<FastNoise, DataOutputStream> genPositionArray2D = (ns, writer) -> {
        // Image data
        final var noiseData = new FloatArray(tow_dimension_size * tow_dimension_size);
        float[] xPos = {0.0f, 0.5f, 0.75f, 1.0f};
        float[] yPos = {1.0f, 1.0f, 2.0f, 2.0f};
        FastNoise.OutputMinMax minMax = ns.genPositionArray2D(noiseData, new FloatArray(xPos), new FloatArray(yPos), 1, 2, 1337);

        float scale = 255.0f / (minMax.max - minMax.min);
        for (float noise : noiseData) {
            // Scale noise to 0 - 255
            int noiseI = Math.round((noise - minMax.min) * scale);
            try {
                writer.writeByte(Math.max(0, Math.min(255, noiseI)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    @Test
    public void testGenUniformGrid2D() {
        gen(tow_dimension_size, maxSmooth, "build/testMetadata", genUniformGrid2D);
        // Simplex fractal ENT
        try (final var fastnoise = FastNoise.fromEncodedNodeTree("DQAFAAAAAAAAQAgAAAAAAD8AAAAAAA==")) {
            if (fastnoise != null) {
                gen(tow_dimension_size, fastnoise, "build/testENT", genUniformGrid2D);
            }
        }
        assertTrue(Files.exists(Paths.get("build/testMetadata.bmp")));
        assertTrue(Files.exists(Paths.get("build/testENT.bmp")));
    }

    @Test
    public void testGenTileable2D() {
        gen(tow_dimension_size, maxSmooth, "build/testMetadata2", genTileable2D);
        // Simplex fractal ENT
        try (final var fastnoise = FastNoise.fromEncodedNodeTree("DQAFAAAAAAAAQAgAAAAAAD8AAAAAAA==")) {
            if (fastnoise != null) {
                gen(tow_dimension_size, fastnoise, "build/testENT2", genTileable2D);
            }
        }
        assertTrue(Files.exists(Paths.get("build/testMetadata2.bmp")));
        assertTrue(Files.exists(Paths.get("build/testENT2.bmp")));
    }

    @Test
    public void testGenPositionArray2D() {
        gen(tow_dimension_size, maxSmooth, "build/testMetadata3", genPositionArray2D);
        // Simplex fractal ENT
        try (final var fastnoise = FastNoise.fromEncodedNodeTree("DQAFAAAAAAAAQAgAAAAAAD8AAAAAAA==")) {
            if (fastnoise != null) {
                gen(tow_dimension_size, fastnoise, "build/testENT3", genPositionArray2D);
            }
        }
        assertTrue(Files.exists(Paths.get("build/testMetadata3.bmp")));
        assertTrue(Files.exists(Paths.get("build/testENT3.bmp")));
    }

    @Test
    public void testGenUniformGrid3D() {
        int size = 5 * 5 * 5;
        final var noiseData = new FloatArray(size);
        FastNoise.OutputMinMax minMax = maxSmooth.genUniformGrid3D(noiseData, 0, 0, 0, 5, 5, 5, 0.02f, 1337);
        assertEquals(noiseData.size(), size);
        assertTrue(noiseData.get(1) <= minMax.max && noiseData.get(1) >= minMax.min);
    }


    @Test
    public void testGenPositionArray3D() {
        FloatArray xPos = new FloatArray(new float[]{0.0f, 0.5f, 0.75f, 1.0f});
        FloatArray yPos = new FloatArray(new float[]{0.0f, 0.5f, 0.75f, 1.0f});
        FloatArray zPos = new FloatArray(new float[]{0.0f, 0.5f, 0.75f, 1.0f});
        final var noiseData = new FloatArray(4 * 4 * 4);
        FastNoise.OutputMinMax minMax = maxSmooth.genPositionArray3D(noiseData, xPos, yPos, zPos, 1, 2, 3, 1337);
        assertEquals(noiseData.size(), 64);
        assertTrue(noiseData.get(1) <= minMax.max && noiseData.get(1) >= minMax.min);
    }

    @Test
    public void testGenUniformGrid4D() {
        int size = 2 * 2 * 2 * 2;
        final var noiseData = new FloatArray(size);
        FastNoise.OutputMinMax minMax = maxSmooth.genUniformGrid4D(noiseData, 0, 0, 0, 0, 2, 2, 2, 2, 0.02f, 1337);
        assertEquals(noiseData.size(), size);
        assertTrue(noiseData.get(1) <= minMax.max && noiseData.get(1) >= minMax.min);
    }

    @Test
    public void testGenPositionArray4D() {
        FloatArray xPos = new FloatArray(new float[]{0.0f, 0.5f, 0.75f, 1.0f});
        FloatArray yPos = new FloatArray(new float[]{0.0f, 0.5f, 0.75f, 1.0f});
        FloatArray zPos = new FloatArray(new float[]{0.0f, 0.5f, 0.75f, 1.0f});
        FloatArray wPos = new FloatArray(new float[]{0.0f, 0.5f, 0.75f, 1.0f});
        final var noiseData = new FloatArray(4 * 4 * 4 * 4);
        FastNoise.OutputMinMax minMax = maxSmooth.genPositionArray4D(noiseData, xPos, yPos, zPos, wPos, 1, 2, 3, 4, 1337);
        assertEquals(noiseData.size(), 4 * 4 * 4 * 4);
        assertTrue(noiseData.get(1) <= minMax.max && noiseData.get(1) >= minMax.min);
    }

    @Test
    public void testGenSingle2D() {
        float v = maxSmooth.genSingle2D(1, 2, 1337);
        assertTrue(v <= Float.MAX_VALUE && v >= Float.MIN_VALUE);
    }

    @Test
    public void testGenSingle3D() {
        float v = maxSmooth.genSingle3D(1, 2, 3, 1337);
        assertTrue(v <= Float.MAX_VALUE && v >= Float.MIN_VALUE);
    }

    @Test
    public void testGenSingle4D() {
        float v = maxSmooth.genSingle4D(1, 2, 3, 4, 1337);
        assertTrue(v <= Float.MAX_VALUE && v >= Float.MIN_VALUE);
    }
}