package com.github.fastnoise;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class FloatArray implements Iterable<Float>, AutoCloseable {
    private static final Cleaner cleaner = Cleaner.create();

    private final Arena memory;
    private final MemorySegment segment;
    private final int size;
    private final Cleaner.Cleanable cleanable; // Holds the cleanable task for this object

    // Cleanup action class that will be registered with the Cleaner
    private record MemoryCleanup(Arena memory) implements Runnable {
        @Override
        public void run() {
            memory.close(); // Ensure the memory arena is closed
        }
    }

    // Constructor to allocate a new FloatArray of specified size
    public FloatArray(int size) {
        memory = Arena.ofConfined();
        this.segment = memory.allocate(ValueLayout.JAVA_FLOAT, size);
        this.size = size;
        this.cleanable = cleaner.register(this, new MemoryCleanup(memory));
    }

    // Constructor to create FloatArray from an existing float array
    public FloatArray(float[] array) {
        memory = Arena.ofConfined();
        this.size = array.length;
        this.segment = memory.allocate(ValueLayout.JAVA_FLOAT, size);
        for (int i = 0; i < size; i++) {
            segment.setAtIndex(ValueLayout.JAVA_FLOAT, i, array[i]);
        }
        this.cleanable = cleaner.register(this, new MemoryCleanup(memory));
    }

    public void set(int index, float value) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
        }
        segment.setAtIndex(ValueLayout.JAVA_FLOAT, index, value);
    }

    public float get(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
        }
        return segment.getAtIndex(ValueLayout.JAVA_FLOAT, index);
    }

    public MemorySegment getSegment() {
        return segment;
    }

    public int size() {
        return size;
    }

    @Override
    public Iterator<Float> iterator() {
        return new FloatIterator();
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private class FloatIterator implements Iterator<Float> {
        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < size();
        }

        @Override
        public Float next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return segment.getAtIndex(ValueLayout.JAVA_FLOAT, currentIndex++);
        }
    }
}
