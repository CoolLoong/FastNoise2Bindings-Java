package com.coolloong.fastnoise;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.*;

@SuppressWarnings("preview")
public class FastNoise implements AutoCloseable {
    public static class OutputMinMax {
        public float min;
        public float max;

        public OutputMinMax(float[] nativeOutputMinMax) {
            min = nativeOutputMinMax[0];
            max = nativeOutputMinMax[1];
        }

        public void merge(OutputMinMax other) {
            min = Math.min(min, other.min);
            max = Math.max(max, other.max);
        }
    }

    private static final AddressLayout C_POINTER = ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));

    private static final String OS_SYSTEM_PROPERTY = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    private static final String OS;
    private static final String ARCH = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
    private static final String NATIVE_LIB_PATH = System.getProperty("fastnoise_lib_path", "");
    private static Throwable unavailabilityCause;

    static {
        if (OS_SYSTEM_PROPERTY.startsWith("mac")) {
            OS = "darwin";
        } else if (OS_SYSTEM_PROPERTY.startsWith("win")) {
            OS = "windows";
        } else {
            OS = OS_SYSTEM_PROPERTY;
        }

        String path = NATIVE_LIB_PATH.isEmpty() ? "/" + determineLoadPath() : NATIVE_LIB_PATH;

        try {
            copyNativeLib(path);
            // It is available
            unavailabilityCause = null;
        } catch (Throwable e) {
            unavailabilityCause = e;
        }
    }

    private static void copyNativeLib(String path) {
        try {
            InputStream nativeLib = FastNoise.class.getResourceAsStream(path);
            if (nativeLib == null) {
                // in case the user is trying to load native library from an absolute path
                Path libPath = Paths.get(path);
                if (Files.exists(libPath) && Files.isRegularFile(libPath)) {
                    nativeLib = new FileInputStream(path);
                } else {
                    throw new IllegalStateException("FastNoise Native library " + path + " not found.");
                }
            }

            Path tempFile = createTemporaryNativeFilename(path.substring(path.lastIndexOf('.')));
            Files.copy(nativeLib, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }));

            nativeLibPath = tempFile.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Unable to copy natives", e);
        }
    }

    private static Path createTemporaryNativeFilename(String ext) throws IOException {
        return Files.createTempFile("native-", ext);
    }

    private static String determineLoadPath() {
        return OS + "/" + ARCH + "/fastnoise" + determineDylibSuffix();
    }

    private static String determineDylibSuffix() {
        if (OS.startsWith("darwin")) {
            return ".dylib";
        } else if (OS.startsWith("win")) {
            return ".dll";
        } else {
            return ".so";
        }
    }

    public static boolean isAvailable() {
        return unavailabilityCause == null;
    }

    public static Throwable unavailabilityCause() {
        return unavailabilityCause;
    }

    public static void ensureAvailable() {
        if (unavailabilityCause != null) {
            throw new RuntimeException("FastNoise library unavailable", unavailabilityCause);
        }
    }

    static Path nativeLibPath;
    protected final Arena arena;
    protected final Linker linker;
    protected final SymbolLookup lookup;

    protected final MethodHandle fnNewFromMetadata;
    protected final MethodHandle fnNewFromEncodedNodeTree;
    protected final MethodHandle fnDeleteNodeRef;
    protected final MethodHandle fnGetSIMDLevel;
    protected final MethodHandle fnGetMetadataID;
    protected final MethodHandle fnGenUniformGrid2D;
    protected final MethodHandle fnGenUniformGrid3D;
    protected final MethodHandle fnGenUniformGrid4D;
    protected final MethodHandle fnGenTileable2D;
    protected final MethodHandle fnGenPositionArray2D;
    protected final MethodHandle fnGenPositionArray3D;
    protected final MethodHandle fnGenPositionArray4D;
    protected final MethodHandle fnGenSingle2D;
    protected final MethodHandle fnGenSingle3D;
    protected final MethodHandle fnGenSingle4D;
    protected final MethodHandle fnGetMetadataCount;
    protected final MethodHandle fnGetMetadataName;
    protected final MethodHandle fnGetMetadataVariableCount;
    protected final MethodHandle fnGetMetadataVariableName;
    protected final MethodHandle fnGetMetadataVariableType;
    protected final MethodHandle fnGetMetadataVariableDimensionIdx;
    protected final MethodHandle fnGetMetadataEnumCount;
    protected final MethodHandle fnGetMetadataEnumName;
    protected final MethodHandle fnSetVariableFloat;
    protected final MethodHandle fnSetVariableIntEnum;
    protected final MethodHandle fnGetMetadataNodeLookupCount;
    protected final MethodHandle fnGetMetadataNodeLookupName;
    protected final MethodHandle fnGetMetadataNodeLookupDimensionIdx;
    protected final MethodHandle fnSetNodeLookup;
    protected final MethodHandle fnGetMetadataHybridCount;
    protected final MethodHandle fnGetMetadataHybridName;
    protected final MethodHandle fnGetMetadataHybridDimensionIdx;
    protected final MethodHandle fnSetHybridNodeLookup;
    protected final MethodHandle fnSetHybridFloat;

    public FastNoise() {
        this.arena = Arena.ofConfined();
        this.linker = Linker.nativeLinker();
        this.lookup = SymbolLookup.libraryLookup(nativeLibPath, arena);

        fnNewFromMetadata = linker.downcallHandle(
                lookup.find("fnNewFromMetadata").get(),
                FunctionDescriptor.of(C_POINTER, JAVA_INT, JAVA_INT)
        );

        fnNewFromEncodedNodeTree = linker.downcallHandle(
                lookup.find("fnNewFromEncodedNodeTree").get(),
                FunctionDescriptor.of(C_POINTER, ADDRESS, JAVA_INT)
        );

        fnDeleteNodeRef = linker.downcallHandle(
                lookup.find("fnDeleteNodeRef").get(),
                FunctionDescriptor.ofVoid(C_POINTER)
        );

        fnGetSIMDLevel = linker.downcallHandle(
                lookup.find("fnGetSIMDLevel").get(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER)
        );

        fnGetMetadataID = linker.downcallHandle(
                lookup.find("fnGetMetadataID").get(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER)
        );

        fnGenUniformGrid2D = linker.downcallHandle(
                lookup.find("fnGenUniformGrid2D").get(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER, MemoryLayout.sequenceLayout(JAVA_FLOAT),
                        JAVA_INT, JAVA_INT,
                        JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, MemoryLayout.sequenceLayout(JAVA_FLOAT))
        );

        fnGenUniformGrid3D = linker.downcallHandle(
                lookup.find("fnGenUniformGrid3D").get(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER, MemoryLayout.sequenceLayout(JAVA_FLOAT),
                        JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, MemoryLayout.sequenceLayout(JAVA_FLOAT))
        );

        fnGenUniformGrid4D = linker.downcallHandle(
                lookup.find("fnGenUniformGrid4D").get(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER, MemoryLayout.sequenceLayout(JAVA_FLOAT),
                        JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, MemoryLayout.sequenceLayout(JAVA_FLOAT))
        );

        fnGenTileable2D = linker.downcallHandle(
                lookup.find("fnGenTileable2D").get(),
                FunctionDescriptor.ofVoid(C_POINTER, MemoryLayout.sequenceLayout(JAVA_FLOAT),
                        JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, MemoryLayout.sequenceLayout(JAVA_FLOAT))
        );

        fnGenPositionArray2D = linker.downcallHandle(
                lookup.find("fnGenPositionArray2D").get(),
                FunctionDescriptor.ofVoid(C_POINTER, MemoryLayout.sequenceLayout(JAVA_FLOAT), JAVA_INT,
                        MemoryLayout.sequenceLayout(JAVA_FLOAT), MemoryLayout.sequenceLayout(JAVA_FLOAT),
                        JAVA_FLOAT, JAVA_FLOAT,
                        JAVA_INT, MemoryLayout.sequenceLayout(JAVA_FLOAT))
        );

        fnGenPositionArray3D = linker.downcallHandle(
                lookup.find("fnGenPositionArray3D").get(),
                FunctionDescriptor.ofVoid(C_POINTER, MemoryLayout.sequenceLayout(JAVA_FLOAT), JAVA_INT,
                        MemoryLayout.sequenceLayout(JAVA_FLOAT), MemoryLayout.sequenceLayout(JAVA_FLOAT), MemoryLayout.sequenceLayout(JAVA_FLOAT),
                        JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                        JAVA_INT, MemoryLayout.sequenceLayout(JAVA_FLOAT))
        );

        fnGenPositionArray4D = linker.downcallHandle(
                lookup.find("fnGenPositionArray4D").get(),
                FunctionDescriptor.ofVoid(C_POINTER, MemoryLayout.sequenceLayout(JAVA_FLOAT), JAVA_INT,
                        MemoryLayout.sequenceLayout(JAVA_FLOAT), MemoryLayout.sequenceLayout(JAVA_FLOAT), MemoryLayout.sequenceLayout(JAVA_FLOAT), MemoryLayout.sequenceLayout(JAVA_FLOAT),
                        JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                        JAVA_INT, MemoryLayout.sequenceLayout(JAVA_FLOAT))
        );

        fnGenSingle2D = linker.downcallHandle(
                lookup.find("fnGenSingle2D").get(),
                FunctionDescriptor.of(JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT)
        );

        fnGenSingle3D = linker.downcallHandle(
                lookup.find("fnGenSingle3D").get(),
                FunctionDescriptor.of(JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT)
        );

        fnGenSingle4D = linker.downcallHandle(
                lookup.find("fnGenSingle4D").get(),
                FunctionDescriptor.of(JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT)
        );

        fnGetMetadataCount = linker.downcallHandle(
                lookup.find("fnGetMetadataCount").get(),
                FunctionDescriptor.of(JAVA_INT)
        );

        fnGetMetadataName = linker.downcallHandle(
                lookup.find("fnGetMetadataName").get(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, JAVA_INT)
        );

        fnGetMetadataVariableCount = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableCount").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );

        fnGetMetadataVariableName = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableName").get(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataVariableType = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableType").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataVariableDimensionIdx = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableDimensionIdx").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataEnumCount = linker.downcallHandle(
                lookup.find("fnGetMetadataEnumCount").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataEnumName = linker.downcallHandle(
                lookup.find("fnGetMetadataEnumName").get(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnSetVariableFloat = linker.downcallHandle(
                lookup.find("fnSetVariableFloat").get(),
                FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, JAVA_INT, JAVA_FLOAT)
        );

        fnSetVariableIntEnum = linker.downcallHandle(
                lookup.find("fnSetVariableIntEnum").get(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataNodeLookupCount = linker.downcallHandle(
                lookup.find("fnGetMetadataNodeLookupCount").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );

        fnGetMetadataNodeLookupName = linker.downcallHandle(
                lookup.find("fnGetMetadataNodeLookupName").get(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataNodeLookupDimensionIdx = linker.downcallHandle(
                lookup.find("fnGetMetadataNodeLookupDimensionIdx").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnSetNodeLookup = linker.downcallHandle(
                lookup.find("fnSetNodeLookup").get(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, C_POINTER)
        );

        fnGetMetadataHybridCount = linker.downcallHandle(
                lookup.find("fnGetMetadataHybridCount").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );

        fnGetMetadataHybridName = linker.downcallHandle(
                lookup.find("fnGetMetadataHybridName").get(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataHybridDimensionIdx = linker.downcallHandle(
                lookup.find("fnGetMetadataHybridDimensionIdx").get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnSetHybridNodeLookup = linker.downcallHandle(
                lookup.find("fnSetHybridNodeLookup").get(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, C_POINTER)
        );

        fnSetHybridFloat = linker.downcallHandle(
                lookup.find("fnSetHybridFloat").get(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, JAVA_FLOAT)
        );
    }

    @Override
    public void close() throws Exception {
        arena.close();
    }

    public MemorySegment fnNewFromMetadata(int id, int simdLevel) {
        try {
            return (MemorySegment) fnNewFromMetadata.invokeExact(id, simdLevel);//void point
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public MemorySegment fnNewFromEncodedNodeTree(String encodedString, int simdLevel) {
        try {
            return (MemorySegment) fnNewFromEncodedNodeTree.invokeExact(encodedString, simdLevel);//void point
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void fnDeleteNodeRef(MemorySegment nodeHandle) {
        try {
            fnDeleteNodeRef.invokeExact(nodeHandle);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetSIMDLevel(MemorySegment nodeHandle) {
        try {
            return (int) fnGetSIMDLevel.invokeExact(nodeHandle);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataID(MemorySegment nodeHandle) {
        try {
            return (int) fnGetMetadataID.invokeExact(nodeHandle);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGenUniformGrid2D(MemorySegment nodeHandle, float[] noiseOut,
                                  int xStart, int yStart,
                                  int xSize, int ySize,
                                  float frequency, int seed, float[] outputMinMax) {
        try {
            return (int) fnGenUniformGrid2D.invokeExact(nodeHandle, noiseOut, xStart, yStart, xSize, ySize, frequency, seed, outputMinMax);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGenUniformGrid3D(MemorySegment nodeHandle, float[] noiseOut,
                                  int xStart, int yStart, int zStart,
                                  int xSize, int ySize, int zSize,
                                  float frequency, int seed, float[] outputMinMax) {
        try {
            return (int) fnGenUniformGrid3D.invokeExact(nodeHandle, noiseOut,
                    xStart, yStart, zStart,
                    xSize, ySize, zSize,
                    frequency, seed, outputMinMax);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGenUniformGrid4D(MemorySegment nodeHandle, float[] noiseOut,
                                  int xStart, int yStart, int zStart, int wStart,
                                  int xSize, int ySize, int zSize, int wSize,
                                  float frequency, int seed, float[] outputMinMax) {
        try {
            return (int) fnGenUniformGrid4D.invokeExact(nodeHandle, noiseOut,
                    xStart, yStart, zStart, wStart,
                    xSize, ySize, zSize, wSize,
                    frequency, seed, outputMinMax);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void fnGenTileable2D(MemorySegment nodeHandle, float[] noiseOut,
                                int xSize, int ySize,
                                float frequency, int seed, float[] outputMinMax) {
        try {
            fnGenTileable2D.invokeExact(nodeHandle, noiseOut, xSize, ySize, frequency, seed, outputMinMax);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void fnGenPositionArray2D(MemorySegment nodeHandle, float[] noiseOut, int count,
                                     float[] xPosArray, float[] yPosArray,
                                     float xOffset, float yOffset,
                                     int seed, float[] outputMinMax) {
        try {
            fnGenPositionArray2D.invokeExact(nodeHandle, noiseOut, count,
                    xPosArray, yPosArray,
                    xOffset, yOffset,
                    seed, outputMinMax);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void fnGenPositionArray3D(MemorySegment nodeHandle, float[] noiseOut, int count,
                                     float[] xPosArray, float[] yPosArray, float[] zPosArray,
                                     float xOffset, float yOffset, float zOffset,
                                     int seed, float[] outputMinMax) {
        try {
            fnGenPositionArray3D.invokeExact(nodeHandle, noiseOut, count,
                    xPosArray, yPosArray, zPosArray,
                    xOffset, yOffset, zOffset,
                    seed, outputMinMax);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void fnGenPositionArray4D(MemorySegment nodeHandle, float[] noiseOut, int count,
                                     float[] xPosArray, float[] yPosArray, float[] zPosArray, float[] wPosArray,
                                     float xOffset, float yOffset, float zOffset, float wOffset,
                                     int seed, float[] outputMinMax) {
        try {
            fnGenPositionArray4D.invokeExact(nodeHandle, noiseOut, count,
                    xPosArray, yPosArray, zPosArray, wPosArray,
                    xOffset, yOffset, zOffset, wOffset,
                    seed, outputMinMax);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public float fnGenSingle2D(MemorySegment nodeHandle, float x, float y, int seed) {
        try {
            return (float) fnGenSingle2D.invokeExact(nodeHandle, x, y, seed);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public float fnGenSingle3D(MemorySegment nodeHandle, float x, float y, float z, int seed) {
        try {
            return (float) fnGenSingle3D.invokeExact(nodeHandle, x, y, z, seed);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public float fnGenSingle4D(MemorySegment nodeHandle, float x, float y, float z, float w, int seed) {
        try {
            return (float) fnGenSingle4D.invokeExact(nodeHandle, x, y, z, w, seed);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataCount() {
        try {
            return (int) fnGetMetadataCount.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public String fnGetMetadataName(int id) {
        try {
            return (String) fnGetMetadataName.invokeExact(id);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataVariableCount(int id) {
        try {
            return (int) fnGetMetadataVariableCount.invokeExact(id);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public String fnGetMetadataVariableName(int id, int variableIndex) {
        try {
            return (String) fnGetMetadataVariableName.invokeExact(id, variableIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataVariableType(int id, int variableIndex) {
        try {
            return (int) fnGetMetadataVariableType.invokeExact(id, variableIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataVariableDimensionIdx(int id, int variableIndex) {
        try {
            return (int) fnGetMetadataVariableDimensionIdx.invokeExact(id, variableIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataEnumCount(int id, int variableIndex) {
        try {
            return (int) fnGetMetadataEnumCount.invokeExact(id, variableIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public String fnGetMetadataEnumName(int id, int variableIndex, int enumIndex) {
        try {
            return (String) fnGetMetadataEnumName.invokeExact(id, variableIndex, enumIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean fnSetVariableFloat(MemorySegment nodeHandle, int variableIndex, float value) {
        try {
            return (boolean) fnSetVariableFloat.invokeExact(nodeHandle, variableIndex, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean fnSetVariableIntEnum(MemorySegment nodeHandle, int variableIndex, int value) {
        try {
            return (boolean) fnSetVariableIntEnum.invokeExact(nodeHandle, variableIndex, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataNodeLookupCount(int id) {
        try {
            return (int) fnGetMetadataNodeLookupCount.invokeExact(id);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataNodeLookupDimensionIdx(int id, int nodeLookupIndex) {
        try {
            return (int) fnGetMetadataNodeLookupDimensionIdx.invokeExact(id, nodeLookupIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean fnSetNodeLookup(MemorySegment nodeHandle, int nodeLookupIndex, MemorySegment nodeLookupHandle) {
        try {
            return (boolean) fnSetNodeLookup.invokeExact(nodeHandle, nodeLookupIndex, nodeLookupHandle);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataHybridCount(int id) {
        try {
            return (int) fnGetMetadataHybridCount.invokeExact(id);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public String fnGetMetadataHybridName(int id, int nodeLookupIndex) {
        try {
            return (String) fnGetMetadataHybridName.invokeExact(id, nodeLookupIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public int fnGetMetadataHybridDimensionIdx(int id, int nodeLookupIndex) {
        try {
            return (int) fnGetMetadataHybridDimensionIdx.invokeExact(id, nodeLookupIndex);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean fnSetHybridNodeLookup(MemorySegment nodeHandle, int nodeLookupIndex, MemorySegment nodeLookupHandle) {
        try {
            return (boolean) fnSetHybridNodeLookup.invokeExact(nodeHandle, nodeLookupIndex, nodeLookupHandle);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean fnSetHybridFloat(MemorySegment nodeHandle, int nodeLookupIndex, float value) {
        try {
            return (boolean) fnSetHybridFloat.invokeExact(nodeHandle, nodeLookupIndex, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}