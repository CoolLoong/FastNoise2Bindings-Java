package com.github.fastnoise;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.*;

public class FastNoise implements AutoCloseable {
    public static class OutputMinMax {
        public float min;
        public float max;

        public OutputMinMax(FloatArray nativeOutputMinMax) {
            min = nativeOutputMinMax.get(0);
            max = nativeOutputMinMax.get(1);
        }

        public void merge(OutputMinMax other) {
            min = Math.min(min, other.min);
            max = Math.max(max, other.max);
        }
    }

    public static class Metadata {

        public static class Member {
            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Type getType() {
                return type;
            }

            public void setType(Type type) {
                this.type = type;
            }

            public int getIndex() {
                return index;
            }

            public void setIndex(int index) {
                this.index = index;
            }

            public HashMap<String, Integer> getEnumNames() {
                return enumNames;
            }

            public void setEnumNames(HashMap<String, Integer> enumNames) {
                this.enumNames = enumNames;
            }

            public enum Type {
                Float,
                Int,
                Enum,
                NodeLookup,
                Hybrid
            }

            public String name;
            public Type type;
            public int index;
            public HashMap<String, Integer> enumNames;

            public Member() {
                enumNames = new HashMap<>();
            }
        }

        public int id;
        public String name;
        public HashMap<String, Member> members;

        public Metadata() {
            members = new HashMap<>();
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setMembers(HashMap<String, Member> members) {
            this.members = members;
        }

        public HashMap<String, Member> getMembers() {
            return members;
        }
    }

    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    private static final String ARCH = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
    private static final String NATIVE_LIB_PATH = System.getProperty("fastnoise_lib_path", "");
    private static final AddressLayout C_POINTER = ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));
    static Path nativeLibPath;

    static final MethodHandle fnNewFromMetadata;
    static final MethodHandle fnNewFromEncodedNodeTree;
    static final MethodHandle fnDeleteNodeRef;
    static final MethodHandle fnGetSIMDLevel;
    static final MethodHandle fnGetMetadataID;
    static final MethodHandle fnGenUniformGrid2D;
    static final MethodHandle fnGenUniformGrid3D;
    static final MethodHandle fnGenUniformGrid4D;
    static final MethodHandle fnGenTileable2D;
    static final MethodHandle fnGenPositionArray2D;
    static final MethodHandle fnGenPositionArray3D;
    static final MethodHandle fnGenPositionArray4D;
    static final MethodHandle fnGenSingle2D;
    static final MethodHandle fnGenSingle3D;
    static final MethodHandle fnGenSingle4D;
    static final MethodHandle fnGetMetadataCount;
    static final MethodHandle fnGetMetadataName;
    static final MethodHandle fnGetMetadataVariableCount;
    static final MethodHandle fnGetMetadataVariableName;
    static final MethodHandle fnGetMetadataVariableType;
    static final MethodHandle fnGetMetadataVariableDimensionIdx;
    static final MethodHandle fnGetMetadataEnumCount;
    static final MethodHandle fnGetMetadataEnumName;
    static final MethodHandle fnSetVariableFloat;
    static final MethodHandle fnSetVariableIntEnum;
    static final MethodHandle fnGetMetadataNodeLookupCount;
    static final MethodHandle fnGetMetadataNodeLookupName;
    static final MethodHandle fnGetMetadataNodeLookupDimensionIdx;
    static final MethodHandle fnSetNodeLookup;
    static final MethodHandle fnGetMetadataHybridCount;
    static final MethodHandle fnGetMetadataHybridName;
    static final MethodHandle fnGetMetadataHybridDimensionIdx;
    static final MethodHandle fnSetHybridNodeLookup;
    static final MethodHandle fnSetHybridFloat;

    private static void copyNativeLib(String path) {
        try {
            InputStream nativeLib = FastNoise.class.getClassLoader().getResourceAsStream(path);
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
            throw new ExternalLibraryException("Unable to copy natives", e);
        }
    }

    private static Path createTemporaryNativeFilename(String ext) throws IOException {
        return Files.createTempFile("native-", ext);
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

    static {
        String architecture = null;
        String os = null;
        if (OS.startsWith("mac")) {
            os = "darwin";
            if (ARCH.contains("aarch64") || ARCH.contains("arm64")) {
                architecture = "arm64";
            } else if (ARCH.contains("x86_64") || ARCH.contains("amd64")) {
                architecture = "x86_64";
            }
        } else if (OS.startsWith("win")) {
            os = "windows";
            if (ARCH.contains("x86_64") || ARCH.contains("amd64")) {
                architecture = "x86_64";
            }
        } else if (OS.startsWith("linux")) {
            os = "linux";
            if (ARCH.contains("aarch64") || ARCH.contains("arm64")) {
                architecture = "arm64";
            } else if (ARCH.contains("x86_64") || ARCH.contains("amd64")) {
                architecture = "x86_64";
            }
        }
        if (architecture == null) {
            throw new ExternalLibraryException("Unsupported operating system: " + os);
        }

        final String resourcePath = architecture + "/FastNoise" + determineDylibSuffix();
        final String path = NATIVE_LIB_PATH.isEmpty() ? resourcePath : NATIVE_LIB_PATH;
        try {
            copyNativeLib(path);
        } catch (Throwable e) {
            throw new ExternalLibraryException("FastNoise library unavailable", e);
        }

        final var linker = Linker.nativeLinker();
        final var lookup = SymbolLookup.libraryLookup(nativeLibPath, Arena.global());

        fnNewFromMetadata = linker.downcallHandle(
                lookup.find("fnNewFromMetadata").orElseThrow(),
                FunctionDescriptor.of(C_POINTER, JAVA_INT, JAVA_INT)
        );

        fnNewFromEncodedNodeTree = linker.downcallHandle(
                lookup.find("fnNewFromEncodedNodeTree").orElseThrow(),
                FunctionDescriptor.of(C_POINTER, C_POINTER, JAVA_INT)
        );

        fnDeleteNodeRef = linker.downcallHandle(
                lookup.find("fnDeleteNodeRef").orElseThrow(),
                FunctionDescriptor.ofVoid(C_POINTER)
        );

        fnGetSIMDLevel = linker.downcallHandle(
                lookup.find("fnGetSIMDLevel").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER)
        );

        fnGetMetadataID = linker.downcallHandle(
                lookup.find("fnGetMetadataID").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER)
        );

        fnGenUniformGrid2D = linker.downcallHandle(
                lookup.find("fnGenUniformGrid2D").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER, C_POINTER,
                        JAVA_INT, JAVA_INT,
                        JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, C_POINTER)
        );

        fnGenUniformGrid3D = linker.downcallHandle(
                lookup.find("fnGenUniformGrid3D").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER, C_POINTER,
                        JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, C_POINTER)
        );

        fnGenUniformGrid4D = linker.downcallHandle(
                lookup.find("fnGenUniformGrid4D").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, C_POINTER, C_POINTER,
                        JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, C_POINTER)
        );

        fnGenTileable2D = linker.downcallHandle(
                lookup.find("fnGenTileable2D").orElseThrow(),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER,
                        JAVA_INT, JAVA_INT,
                        JAVA_FLOAT, JAVA_INT, C_POINTER)
        );

        fnGenPositionArray2D = linker.downcallHandle(
                lookup.find("fnGenPositionArray2D").orElseThrow(),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, JAVA_INT,
                        C_POINTER, C_POINTER,
                        JAVA_FLOAT, JAVA_FLOAT,
                        JAVA_INT, C_POINTER)
        );

        fnGenPositionArray3D = linker.downcallHandle(
                lookup.find("fnGenPositionArray3D").orElseThrow(),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, JAVA_INT,
                        C_POINTER, C_POINTER, C_POINTER,
                        JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                        JAVA_INT, C_POINTER)
        );

        fnGenPositionArray4D = linker.downcallHandle(
                lookup.find("fnGenPositionArray4D").orElseThrow(),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, JAVA_INT,
                        C_POINTER, C_POINTER, C_POINTER, C_POINTER,
                        JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                        JAVA_INT, C_POINTER)
        );

        fnGenSingle2D = linker.downcallHandle(
                lookup.find("fnGenSingle2D").orElseThrow(),
                FunctionDescriptor.of(JAVA_FLOAT, C_POINTER, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT)
        );

        fnGenSingle3D = linker.downcallHandle(
                lookup.find("fnGenSingle3D").orElseThrow(),
                FunctionDescriptor.of(JAVA_FLOAT, C_POINTER, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT)
        );

        fnGenSingle4D = linker.downcallHandle(
                lookup.find("fnGenSingle4D").orElseThrow(),
                FunctionDescriptor.of(JAVA_FLOAT, C_POINTER, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT)
        );

        fnGetMetadataCount = linker.downcallHandle(
                lookup.find("fnGetMetadataCount").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT)
        );

        fnGetMetadataName = linker.downcallHandle(
                lookup.find("fnGetMetadataName").orElseThrow(),
                FunctionDescriptor.of(C_POINTER, JAVA_INT)
        );

        fnGetMetadataVariableCount = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableCount").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );

        fnGetMetadataVariableName = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableName").orElseThrow(),
                FunctionDescriptor.of(C_POINTER, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataVariableType = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableType").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataVariableDimensionIdx = linker.downcallHandle(
                lookup.find("fnGetMetadataVariableDimensionIdx").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataEnumCount = linker.downcallHandle(
                lookup.find("fnGetMetadataEnumCount").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataEnumName = linker.downcallHandle(
                lookup.find("fnGetMetadataEnumName").orElseThrow(),
                FunctionDescriptor.of(C_POINTER, JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnSetVariableFloat = linker.downcallHandle(
                lookup.find("fnSetVariableFloat").orElseThrow(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, JAVA_FLOAT)
        );

        fnSetVariableIntEnum = linker.downcallHandle(
                lookup.find("fnSetVariableIntEnum").orElseThrow(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataNodeLookupCount = linker.downcallHandle(
                lookup.find("fnGetMetadataNodeLookupCount").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );

        fnGetMetadataNodeLookupName = linker.downcallHandle(
                lookup.find("fnGetMetadataNodeLookupName").orElseThrow(),
                FunctionDescriptor.of(C_POINTER, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataNodeLookupDimensionIdx = linker.downcallHandle(
                lookup.find("fnGetMetadataNodeLookupDimensionIdx").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnSetNodeLookup = linker.downcallHandle(
                lookup.find("fnSetNodeLookup").orElseThrow(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, C_POINTER)
        );

        fnGetMetadataHybridCount = linker.downcallHandle(
                lookup.find("fnGetMetadataHybridCount").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT)
        );

        fnGetMetadataHybridName = linker.downcallHandle(
                lookup.find("fnGetMetadataHybridName").orElseThrow(),
                FunctionDescriptor.of(C_POINTER, JAVA_INT, JAVA_INT)
        );

        fnGetMetadataHybridDimensionIdx = linker.downcallHandle(
                lookup.find("fnGetMetadataHybridDimensionIdx").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT)
        );

        fnSetHybridNodeLookup = linker.downcallHandle(
                lookup.find("fnSetHybridNodeLookup").orElseThrow(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, C_POINTER)
        );

        fnSetHybridFloat = linker.downcallHandle(
                lookup.find("fnSetHybridFloat").orElseThrow(),
                FunctionDescriptor.of(JAVA_BOOLEAN, C_POINTER, JAVA_INT, JAVA_FLOAT)
        );
    }

    private static final Metadata[] nodeMetadata;
    private static final HashMap<String, Integer> metadataNameLookup;
    private final MemorySegment mNodeHandle;
    private final int mMetadataId;

    public FastNoise(String metadataName) {
        Integer metadataId = metadataNameLookup.get(formatLookup(metadataName));
        if (metadataId == null) {
            throw new IllegalArgumentException("Failed to find metadata name: " + metadataName);
        }
        mMetadataId = metadataId;

        try {
            mNodeHandle = fnNewFromMetadata(mMetadataId, (short) 0);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    private FastNoise(MemorySegment nodeHandle) {
        mNodeHandle = nodeHandle;
        mMetadataId = fnGetMetadataID(mNodeHandle);
    }

    @Override
    public void close() {
        fnDeleteNodeRef(mNodeHandle);
    }

    public static FastNoise fromEncodedNodeTree(String encodedNodeTree) {
        MemorySegment nodeHandle = fnNewFromEncodedNodeTree(encodedNodeTree, 0);

        if (nodeHandle.equals(MemorySegment.NULL)) {
            return null;
        }

        return new FastNoise(nodeHandle);
    }

    public int getSIMDLevel() {
        try {
            return (int) fnGetSIMDLevel.invokeExact(mNodeHandle);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    public void set(String memberName, float value) {
        final Metadata.Member member = getMember(memberName);
        switch (member.type) {
            case Float -> {
                if (!fnSetVariableFloat(mNodeHandle, member.index, value)) {
                    throw new ExternalLibraryException("Failed to set float value");
                }
            }
            case Hybrid -> {
                if (!fnSetHybridFloat(mNodeHandle, member.index, value)) {
                    throw new ExternalLibraryException("Failed to set float value");
                }
            }
            default -> throw new IllegalArgumentException(memberName + " cannot be set to a float value");
        }
    }

    public void set(String memberName, int value) {
        final Metadata.Member member = getMember(memberName);
        if (member.type != Metadata.Member.Type.Int) {
            throw new IllegalArgumentException(memberName + " cannot be set to an int value");
        }

        if (!fnSetVariableIntEnum(mNodeHandle, member.index, value)) {
            throw new ExternalLibraryException("Failed to set int value");
        }
    }

    public void set(String memberName, String enumValue) {
        final Metadata.Member member = getMember(memberName);
        if (member.type != Metadata.Member.Type.Enum) {
            throw new IllegalArgumentException(memberName + " cannot be set to an enum value");
        }
        final Integer enumIdx = member.enumNames.get(formatLookup(enumValue));
        if (enumIdx == null) {
            throw new IllegalArgumentException("Failed to find enum value: " + enumValue);
        }
        if (!fnSetVariableIntEnum(mNodeHandle, member.index, enumIdx)) {
            throw new ExternalLibraryException("Failed to set enum value");
        }
    }

    public void set(String memberName, FastNoise nodeLookup) {
        final Metadata.Member member = getMember(memberName);
        switch (member.type) {
            case NodeLookup -> {
                if (!fnSetNodeLookup(mNodeHandle, member.index, nodeLookup.mNodeHandle)) {
                    throw new ExternalLibraryException("Failed to set node lookup");
                }
            }
            case Hybrid -> {
                if (!fnSetHybridNodeLookup(mNodeHandle, member.index, nodeLookup.mNodeHandle)) {
                    throw new ExternalLibraryException("Failed to set node lookup");
                }
            }
            default -> throw new IllegalArgumentException(memberName + " cannot be set to a node lookup");
        }
    }

    public OutputMinMax genUniformGrid2D(FloatArray noiseOut, int xStart, int yStart, int xSize, int ySize, float frequency, int seed) {
        final var minMax = new FloatArray(2);
        fnGenUniformGrid2D(mNodeHandle, noiseOut, xStart, yStart, xSize, ySize, frequency, seed, minMax);
        return new OutputMinMax(minMax);
    }

    public OutputMinMax genUniformGrid3D(FloatArray noiseOut, int xStart, int yStart, int zStart, int xSize, int ySize, int zSize, float frequency, int seed) {
        final FloatArray minMax = new FloatArray(2);
        fnGenUniformGrid3D(mNodeHandle, noiseOut, xStart, yStart, zStart, xSize, ySize, zSize, frequency, seed, minMax);
        return new OutputMinMax(minMax);
    }

    public OutputMinMax genUniformGrid4D(FloatArray noiseOut, int xStart, int yStart, int zStart, int wStart, int xSize, int ySize, int zSize, int wSize, float frequency, int seed) {
        final FloatArray minMax = new FloatArray(2);
        fnGenUniformGrid4D(mNodeHandle, noiseOut, xStart, yStart, zStart, wStart, xSize, ySize, zSize, wSize, frequency, seed, minMax);
        return new OutputMinMax(minMax);
    }

    public OutputMinMax genTileable2D(FloatArray noiseOut, int xSize, int ySize, float frequency, int seed) {
        final FloatArray minMax = new FloatArray(2);
        fnGenTileable2D(mNodeHandle, noiseOut, xSize, ySize, frequency, seed, minMax);
        return new OutputMinMax(minMax);
    }

    public OutputMinMax genPositionArray2D(FloatArray noiseOut, FloatArray xPosArray, FloatArray yPosArray, float xOffset, float yOffset, int seed) {
        final FloatArray minMax = new FloatArray(2);
        fnGenPositionArray2D(mNodeHandle, noiseOut, xPosArray.size(), xPosArray, yPosArray, xOffset, yOffset, seed, minMax);
        return new OutputMinMax(minMax);
    }

    public OutputMinMax genPositionArray3D(FloatArray noiseOut, FloatArray xPosArray, FloatArray yPosArray, FloatArray zPosArray, float xOffset, float yOffset, float zOffset, int seed) {
        final FloatArray minMax = new FloatArray(2);
        fnGenPositionArray3D(mNodeHandle, noiseOut, xPosArray.size(), xPosArray, yPosArray, zPosArray, xOffset, yOffset, zOffset, seed, minMax);
        return new OutputMinMax(minMax);
    }

    public OutputMinMax genPositionArray4D(FloatArray noiseOut, FloatArray xPosArray, FloatArray yPosArray, FloatArray zPosArray, FloatArray wPosArray, float xOffset, float yOffset, float zOffset, float wOffset, int seed) {
        final FloatArray minMax = new FloatArray(2);
        fnGenPositionArray4D(mNodeHandle, noiseOut, xPosArray.size(), xPosArray, yPosArray, zPosArray, wPosArray, xOffset, yOffset, zOffset, wOffset, seed, minMax);
        return new OutputMinMax(minMax);
    }

    public float genSingle2D(float x, float y, int seed) {
        return fnGenSingle2D(mNodeHandle, x, y, seed);
    }

    public float genSingle3D(float x, float y, float z, int seed) {
        return fnGenSingle3D(mNodeHandle, x, y, z, seed);
    }

    public float genSingle4D(float x, float y, float z, float w, int seed) {
        return fnGenSingle4D(mNodeHandle, x, y, z, w, seed);
    }

    private Metadata.Member getMember(String memberName) {
        final String key = formatLookup(memberName);
        final Metadata metadata = nodeMetadata[mMetadataId];
        final Metadata.Member member = metadata.members.get(key);
        if (member == null) {
            throw new IllegalArgumentException("Failed to find member name: " + memberName);
        }
        return member;
    }

    private static String formatLookup(String s) {
        return s.replace(" ", "").toLowerCase();
    }

    private static String formatDimensionMember(String name, int dimIdx) {
        if (dimIdx >= 0) {
            char[] dimSuffix = {'x', 'y', 'z', 'w'};
            name += dimSuffix[dimIdx];
        }
        return name;
    }

    static {
        int metadataCount = fnGetMetadataCount();

        nodeMetadata = new Metadata[metadataCount];
        metadataNameLookup = new HashMap<>(metadataCount);

        // Collect metadata for all FastNoise node classes
        for (int id = 0; id < metadataCount; id++) {
            Metadata metadata = new Metadata();
            metadata.setId(id);
            metadata.setName(formatLookup(fnGetMetadataName(id))); // Assuming fnGetMetadataName returns a String

            metadataNameLookup.put(metadata.getName(), id);

            int variableCount = fnGetMetadataVariableCount(id);
            int nodeLookupCount = fnGetMetadataNodeLookupCount(id);
            int hybridCount = fnGetMetadataHybridCount(id);
            metadata.setMembers(new HashMap<>(variableCount + nodeLookupCount + hybridCount));

            // Init variables
            for (int variableIdx = 0; variableIdx < variableCount; variableIdx++) {
                Metadata.Member member = new Metadata.Member();

                member.setName(formatLookup(fnGetMetadataVariableName(id, variableIdx)));
                member.setType(Metadata.Member.Type.values()[fnGetMetadataVariableType(id, variableIdx)]);
                member.setIndex(variableIdx);

                member.setName(formatDimensionMember(member.getName(), fnGetMetadataVariableDimensionIdx(id, variableIdx)));

                // Get enum names
                if (member.getType() == Metadata.Member.Type.Enum) {
                    int enumCount = fnGetMetadataEnumCount(id, variableIdx);
                    HashMap<String, Integer> enumNames = new HashMap<>(enumCount);

                    for (int enumIdx = 0; enumIdx < enumCount; enumIdx++) {
                        enumNames.put(formatLookup(fnGetMetadataEnumName(id, variableIdx, enumIdx)), enumIdx);
                    }
                    member.setEnumNames(enumNames);
                }

                metadata.getMembers().put(member.getName(), member);
            }

            // Init node lookups
            for (int nodeLookupIdx = 0; nodeLookupIdx < nodeLookupCount; nodeLookupIdx++) {
                Metadata.Member member = new Metadata.Member();

                member.setName(formatLookup(fnGetMetadataNodeLookupName(id, nodeLookupIdx)));
                member.setType(Metadata.Member.Type.NodeLookup);
                member.setIndex(nodeLookupIdx);

                member.setName(formatDimensionMember(member.getName(), fnGetMetadataNodeLookupDimensionIdx(id, nodeLookupIdx)));

                metadata.getMembers().put(member.getName(), member);
            }

            // Init hybrids
            for (int hybridIdx = 0; hybridIdx < hybridCount; hybridIdx++) {
                Metadata.Member member = new Metadata.Member();

                member.setName(formatLookup(fnGetMetadataHybridName(id, hybridIdx)));
                member.setType(Metadata.Member.Type.Hybrid);
                member.setIndex(hybridIdx);

                member.setName(formatDimensionMember(member.getName(), fnGetMetadataHybridDimensionIdx(id, hybridIdx)));

                metadata.getMembers().put(member.getName(), member);
            }
            nodeMetadata[id] = metadata;
        }
    }

    static MemorySegment fnNewFromMetadata(int id, int simdLevel) {
        try {
            return (MemorySegment) fnNewFromMetadata.invokeExact(id, simdLevel);//void point
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static MemorySegment fnNewFromEncodedNodeTree(String encodedString, int simdLevel) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment encodedNodeTreeSegment = arena.allocateFrom(encodedString);
            return (MemorySegment) fnNewFromEncodedNodeTree.invokeExact(encodedNodeTreeSegment, simdLevel);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static void fnDeleteNodeRef(MemorySegment nodeHandle) {
        try {
            fnDeleteNodeRef.invokeExact(nodeHandle);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetSIMDLevel(MemorySegment nodeHandle) {
        try {
            return (int) fnGetSIMDLevel.invokeExact(nodeHandle);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataID(MemorySegment nodeHandle) {
        try {
            return (int) fnGetMetadataID.invokeExact(nodeHandle);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGenUniformGrid2D(MemorySegment nodeHandle, FloatArray noiseOut,
                                  int xStart, int yStart,
                                  int xSize, int ySize,
                                  float frequency, int seed, FloatArray outputMinMax) {
        try {
            return (int) fnGenUniformGrid2D.invokeExact(nodeHandle, noiseOut.getSegment(), xStart, yStart, xSize, ySize, frequency, seed, outputMinMax.getSegment());
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGenUniformGrid3D(MemorySegment nodeHandle, FloatArray noiseOut,
                                  int xStart, int yStart, int zStart,
                                  int xSize, int ySize, int zSize,
                                  float frequency, int seed, FloatArray outputMinMax) {
        try {
            return (int) fnGenUniformGrid3D.invokeExact(nodeHandle, noiseOut.getSegment(),
                    xStart, yStart, zStart,
                    xSize, ySize, zSize,
                    frequency, seed, outputMinMax.getSegment());
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGenUniformGrid4D(MemorySegment nodeHandle, FloatArray noiseOut,
                                  int xStart, int yStart, int zStart, int wStart,
                                  int xSize, int ySize, int zSize, int wSize,
                                  float frequency, int seed, FloatArray outputMinMax) {
        try {
            return (int) fnGenUniformGrid4D.invokeExact(nodeHandle, noiseOut.getSegment(),
                    xStart, yStart, zStart, wStart,
                    xSize, ySize, zSize, wSize,
                    frequency, seed, outputMinMax.getSegment());
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static void fnGenTileable2D(MemorySegment nodeHandle, FloatArray noiseOut,
                                int xSize, int ySize,
                                float frequency, int seed, FloatArray outputMinMax) {
        try {
            fnGenTileable2D.invokeExact(nodeHandle, noiseOut.getSegment(), xSize, ySize, frequency, seed, outputMinMax.getSegment());
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static void fnGenPositionArray2D(MemorySegment nodeHandle, FloatArray noiseOut, int count,
                                     FloatArray xPosArray, FloatArray yPosArray,
                                     float xOffset, float yOffset,
                                     int seed, FloatArray outputMinMax) {
        try {
            fnGenPositionArray2D.invokeExact(nodeHandle, noiseOut.getSegment(), count,
                    xPosArray.getSegment(), yPosArray.getSegment(),
                    xOffset, yOffset,
                    seed, outputMinMax.getSegment());
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static void fnGenPositionArray3D(MemorySegment nodeHandle, FloatArray noiseOut, int count,
                                     FloatArray xPosArray, FloatArray yPosArray, FloatArray zPosArray,
                                     float xOffset, float yOffset, float zOffset,
                                     int seed, FloatArray outputMinMax) {
        try {
            fnGenPositionArray3D.invokeExact(nodeHandle, noiseOut.getSegment(), count,
                    xPosArray.getSegment(), yPosArray.getSegment(), zPosArray.getSegment(),
                    xOffset, yOffset, zOffset,
                    seed, outputMinMax.getSegment());
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static void fnGenPositionArray4D(MemorySegment nodeHandle, FloatArray noiseOut, int count,
                                     FloatArray xPosArray, FloatArray yPosArray, FloatArray zPosArray, FloatArray wPosArray,
                                     float xOffset, float yOffset, float zOffset, float wOffset,
                                     int seed, FloatArray outputMinMax) {
        try {
            fnGenPositionArray4D.invokeExact(nodeHandle, noiseOut.getSegment(), count,
                    xPosArray.getSegment(), yPosArray.getSegment(), zPosArray.getSegment(), wPosArray.getSegment(),
                    xOffset, yOffset, zOffset, wOffset,
                    seed, outputMinMax.getSegment());
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static float fnGenSingle2D(MemorySegment nodeHandle, float x, float y, int seed) {
        try {
            return (float) fnGenSingle2D.invokeExact(nodeHandle, x, y, seed);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static float fnGenSingle3D(MemorySegment nodeHandle, float x, float y, float z, int seed) {
        try {
            return (float) fnGenSingle3D.invokeExact(nodeHandle, x, y, z, seed);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static float fnGenSingle4D(MemorySegment nodeHandle, float x, float y, float z, float w, int seed) {
        try {
            return (float) fnGenSingle4D.invokeExact(nodeHandle, x, y, z, w, seed);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataCount() {
        try {
            return (int) fnGetMetadataCount.invokeExact();
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static String fnGetMetadataName(int id) {
        try {
            final MemorySegment segment = (MemorySegment) fnGetMetadataName.invokeExact(id);
            return segment.getString(0);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataVariableCount(int id) {
        try {
            return (int) fnGetMetadataVariableCount.invokeExact(id);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static String fnGetMetadataVariableName(int id, int variableIndex) {
        try {
            final MemorySegment segment = (MemorySegment) fnGetMetadataVariableName.invokeExact(id, variableIndex);
            return segment.getString(0);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataVariableType(int id, int variableIndex) {
        try {
            return (int) fnGetMetadataVariableType.invokeExact(id, variableIndex);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataVariableDimensionIdx(int id, int variableIndex) {
        try {
            return (int) fnGetMetadataVariableDimensionIdx.invokeExact(id, variableIndex);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataEnumCount(int id, int variableIndex) {
        try {
            return (int) fnGetMetadataEnumCount.invokeExact(id, variableIndex);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static String fnGetMetadataEnumName(int id, int variableIndex, int enumIndex) {
        try {
            final MemorySegment segment = (MemorySegment) fnGetMetadataEnumName.invokeExact(id, variableIndex, enumIndex);
            return segment.getString(0);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static boolean fnSetVariableFloat(MemorySegment nodeHandle, int variableIndex, float value) {
        try {
            return (boolean) fnSetVariableFloat.invokeExact(nodeHandle, variableIndex, value);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static boolean fnSetVariableIntEnum(MemorySegment nodeHandle, int variableIndex, int value) {
        try {
            return (boolean) fnSetVariableIntEnum.invokeExact(nodeHandle, variableIndex, value);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataNodeLookupCount(int id) {
        try {
            return (int) fnGetMetadataNodeLookupCount.invokeExact(id);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static String fnGetMetadataNodeLookupName(int id, int nodeLookupIndex) {
        try {
            final MemorySegment segment = (MemorySegment) fnGetMetadataNodeLookupName.invokeExact(id, nodeLookupIndex);
            return segment.getString(0);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataNodeLookupDimensionIdx(int id, int nodeLookupIndex) {
        try {
            return (int) fnGetMetadataNodeLookupDimensionIdx.invokeExact(id, nodeLookupIndex);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static boolean fnSetNodeLookup(MemorySegment nodeHandle, int nodeLookupIndex, MemorySegment nodeLookupHandle) {
        try {
            return (boolean) fnSetNodeLookup.invokeExact(nodeHandle, nodeLookupIndex, nodeLookupHandle);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataHybridCount(int id) {
        try {
            return (int) fnGetMetadataHybridCount.invokeExact(id);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static String fnGetMetadataHybridName(int id, int nodeLookupIndex) {
        try {
            final MemorySegment segment = (MemorySegment) fnGetMetadataHybridName.invokeExact(id, nodeLookupIndex);
            return segment.getString(0);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static int fnGetMetadataHybridDimensionIdx(int id, int nodeLookupIndex) {
        try {
            return (int) fnGetMetadataHybridDimensionIdx.invokeExact(id, nodeLookupIndex);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static boolean fnSetHybridNodeLookup(MemorySegment nodeHandle, int nodeLookupIndex, MemorySegment nodeLookupHandle) {
        try {
            return (boolean) fnSetHybridNodeLookup.invokeExact(nodeHandle, nodeLookupIndex, nodeLookupHandle);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }

    static boolean fnSetHybridFloat(MemorySegment nodeHandle, int nodeLookupIndex, float value) {
        try {
            return (boolean) fnSetHybridFloat.invokeExact(nodeHandle, nodeLookupIndex, value);
        } catch (Throwable e) {
            throw new ExternalLibraryException(e);
        }
    }
}