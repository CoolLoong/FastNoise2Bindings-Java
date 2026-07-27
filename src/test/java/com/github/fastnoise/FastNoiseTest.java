package com.github.fastnoise;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void testFastNoiseConstructionConfigurationAndLifecycle() {
        assertThrows(IllegalArgumentException.class, () -> new FastNoise("missing node"));
        assertNull(FastNoise.fromEncodedNodeTree("not an encoded node tree"));

        try (FastNoise source = new FastNoise("Simplex");
             FastNoise fractal = new FastNoise("FractalFBm");
             FastNoise cellular = new FastNoise("CellularDistance")) {
            fractal.set("Source", source);
            fractal.set("Gain", 0.5f);
            cellular.set("DistanceIndex0", 2);
            cellular.set("ReturnType", "Index0Add1");

            assertTrue(fractal.getSIMDLevel() > 0);
            assertThrows(IllegalArgumentException.class, () -> fractal.set("Source", 1.0f));
            assertThrows(IllegalArgumentException.class, () -> fractal.set("Gain", 1));
            assertThrows(IllegalArgumentException.class, () -> fractal.set("Gain", "Index0Add1"));
            fractal.set("Gain", source);
            assertThrows(IllegalArgumentException.class, () -> fractal.set("missing", source));
        }
    }

    @Test
    public void testFromEncodedNodeTree() {
        try (FastNoise nodeTree = FastNoise.fromEncodedNodeTree("EQkI@BykIIAg@BQDAACCQgQ=")) {
            assertNotNull(nodeTree);
            gen(nodePreviewSize, nodeTree, "build/testENT", genNodePreview2D);
            assertTrue(Files.exists(Paths.get("build/testENT.bmp")));
        }
    }

    @Test
    public void testOutputMinMaxAndMetadataApi() {
        try (FloatArray firstData = new FloatArray(new float[]{-2.0f, 3.0f});
             FloatArray secondData = new FloatArray(new float[]{-4.0f, 1.0f})) {
            FastNoise.OutputMinMax first = new FastNoise.OutputMinMax(firstData);
            FastNoise.OutputMinMax second = new FastNoise.OutputMinMax(secondData);
            first.merge(second);
            assertEquals(-4.0f, first.min);
            assertEquals(3.0f, first.max);
        }

        FastNoise.Metadata metadata = new FastNoise.Metadata();
        metadata.setId(7);
        metadata.setName("test");
        HashMap<String, FastNoise.Metadata.Member> members = new HashMap<>();
        metadata.setMembers(members);
        assertEquals(7, metadata.getId());
        assertEquals("test", metadata.getName());
        assertSame(members, metadata.getMembers());

        FastNoise.Metadata.Member member = new FastNoise.Metadata.Member();
        HashMap<String, Integer> enumNames = new HashMap<>();
        enumNames.put("value", 1);
        member.setName("member");
        member.setType(FastNoise.Metadata.Member.Type.Enum);
        member.setIndex(3);
        member.setEnumNames(enumNames);
        assertEquals("member", member.getName());
        assertEquals(FastNoise.Metadata.Member.Type.Enum, member.getType());
        assertEquals(3, member.getIndex());
        assertSame(enumNames, member.getEnumNames());
        assertEquals(5, FastNoise.Metadata.Member.Type.values().length);
    }

    @Test
    public void testFloatArrayApi() {
        try (FloatArray allocated = new FloatArray(2);
             FloatArray copied = new FloatArray(new float[]{1.0f, 2.0f})) {
            allocated.set(0, -1.0f);
            allocated.set(1, 4.0f);
            assertEquals(-1.0f, allocated.get(0));
            assertEquals(4.0f, allocated.get(1));
            assertEquals(2, allocated.size());
            assertEquals(2L * Float.BYTES, allocated.getSegment().byteSize());
            assertEquals(List.of(1.0f, 2.0f), toList(copied));
            assertThrows(IndexOutOfBoundsException.class, () -> allocated.get(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> allocated.set(2, 0.0f));
        }
    }

    @Test
    public void testExternalLibraryExceptionConstructors() {
        Exception exception = new Exception("cause");
        Throwable throwable = new Throwable("cause");
        assertEquals("message", new ExternalLibraryException("message").getMessage());
        assertSame(exception, new ExternalLibraryException("message", exception).getCause());
        assertSame(throwable, new ExternalLibraryException("message", throwable).getCause());
        assertSame(throwable, new ExternalLibraryException(throwable).getCause());
    }

    private static List<Float> toList(FloatArray values) {
        List<Float> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
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

    final int nodePreviewSize = 224;
    final int nodePreviewStart = -nodePreviewSize / 2;
    final float nodePreviewScale = 2.5f;
    final int nodePreviewSeed = 1337;

    private void writeNodePreview(DataOutputStream writer, FloatArray pixels) {
        try {
            for (float pixel : pixels) {
                writer.writeByte(Float.floatToRawIntBits(pixel));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private FastNoise nodePreviewConverter(FastNoise source) {
        FastNoise converter = new FastNoise("ConvertRGBA8");
        converter.set("Source", source);
        return converter;
    }

    BiConsumer<FastNoise, DataOutputStream> genNodePreview2D = (source, writer) -> {
        try (FastNoise converter = nodePreviewConverter(source);
             FloatArray pixels = new FloatArray(nodePreviewSize * nodePreviewSize)) {
            converter.genUniformGrid2D(pixels, nodePreviewStart, nodePreviewStart,
                    nodePreviewSize, nodePreviewSize, nodePreviewScale, nodePreviewSeed);
            writeNodePreview(writer, pixels);
        }
    };

    BiConsumer<FastNoise, DataOutputStream> genNodePreviewTileable2D = (source, writer) -> {
        try (FastNoise converter = nodePreviewConverter(source);
             FloatArray pixels = new FloatArray(nodePreviewSize * nodePreviewSize)) {
            converter.genTileable2D(pixels, nodePreviewSize, nodePreviewSize, nodePreviewScale, nodePreviewSeed);
            writeNodePreview(writer, pixels);
        }
    };

    BiConsumer<FastNoise, DataOutputStream> genNodePreviewPositionArray2D = (source, writer) -> {
        int count = nodePreviewSize * nodePreviewSize;
        try (FastNoise converter = nodePreviewConverter(source);
             FloatArray pixels = new FloatArray(count);
             FloatArray xPositions = new FloatArray(count);
             FloatArray yPositions = new FloatArray(count)) {
            for (int y = 0; y < nodePreviewSize; y++) {
                for (int x = 0; x < nodePreviewSize; x++) {
                    int index = y * nodePreviewSize + x;
                    xPositions.set(index, (nodePreviewStart + x) * nodePreviewScale);
                    yPositions.set(index, (nodePreviewStart + y) * nodePreviewScale);
                }
            }
            converter.genPositionArray2D(pixels, xPositions, yPositions, 0.0f, 0.0f, nodePreviewSeed);
            writeNodePreview(writer, pixels);
        }
    };

    @Test
    public void testGenUniformGrid2D() {
        gen(nodePreviewSize, maxSmooth, "build/testMetadata", genNodePreview2D);
        assertTrue(Files.exists(Paths.get("build/testMetadata.bmp")));
    }

    @Test
    public void testGenTileable2D() {
        gen(nodePreviewSize, maxSmooth, "build/testMetadata2", genNodePreviewTileable2D);
        assertTrue(Files.exists(Paths.get("build/testMetadata2.bmp")));
    }

    @Test
    public void testGenPositionArray2D() {
        gen(nodePreviewSize, maxSmooth, "build/testMetadata3", genNodePreviewPositionArray2D);
        assertTrue(Files.exists(Paths.get("build/testMetadata3.bmp")));
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
    public void testDomainAxisScale() {
        try (FastNoise source = new FastNoise("Simplex");
             FastNoise axisScale = new FastNoise("DomainAxisScale")) {
            axisScale.set("Source", source);
            axisScale.set("ScalingX", 1.0f);
            axisScale.set("ScalingY", 1.0f);
            axisScale.set("ScalingZ", 1.0f);

            float[][] positions = {{0.125f, -0.75f, 1.5f}, {-2.25f, 0.5f, -1.0f}};
            for (float[] position : positions) {
                assertEquals(Float.floatToRawIntBits(source.genSingle3D(position[0], position[1], position[2], 1337)),
                        Float.floatToRawIntBits(axisScale.genSingle3D(position[0], position[1], position[2], 1337)));
            }

            axisScale.set("ScalingX", 2.0f);
            boolean differsFromRaw = false;
            for (float[] position : positions) {
                int rawBits = Float.floatToRawIntBits(source.genSingle3D(position[0], position[1], position[2], 1337));
                int scaledBits = Float.floatToRawIntBits(axisScale.genSingle3D(position[0], position[1], position[2], 1337));
                assertEquals(Float.floatToRawIntBits(source.genSingle3D(2.0f * position[0], position[1], position[2], 1337)), scaledBits);
                differsFromRaw |= scaledBits != rawBits;
            }
            assertTrue(differsFromRaw);
        }
    }

    @Test
    public void testGenUniformGrid2DMatchesSingleAtNonzeroStart() {
        final int xStart = 37;
        final int yStart = -19;
        final float frequency = 0.02f;
        final var noiseData = new FloatArray(1);
        maxSmooth.genUniformGrid2D(noiseData, xStart, yStart, 1, 1, frequency, 1337);
        assertEquals(Float.floatToRawIntBits(maxSmooth.genSingle2D(xStart * frequency, yStart * frequency, 1337)),
                Float.floatToRawIntBits(noiseData.get(0)));
    }

    @Test
    public void testGenSingle2D() {
        float v = maxSmooth.genSingle2D(1, 2, 1337);
        assertTrue(Float.isFinite(v));
    }

    @Test
    public void testGenSingle3D() {
        float v = maxSmooth.genSingle3D(1, 2, 3, 1337);
        assertTrue(Float.isFinite(v));
    }

    @Test
    public void testGenSingle4D() {
        float v = maxSmooth.genSingle4D(1, 2, 3, 4, 1337);
        assertTrue(Float.isFinite(v));
    }
}