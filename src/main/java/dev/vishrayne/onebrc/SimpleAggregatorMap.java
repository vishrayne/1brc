package dev.vishrayne.onebrc;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class SimpleAggregatorMap<K, V> {
    public static final int DEFAULT_CAPACITY = 16; // Must be power of 2
    public static final float DEFAULT_LOAD_FACTOR = 0.7f;

    private K[] keys;
    private V[] values;
    private int capacity;
    private int resizeThreshold;
    private int size;
    private final float loadFactor;

    @SuppressWarnings("unchecked")
    public SimpleAggregatorMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity must be positive");
        }

        this.capacity = 1;
        while (capacity < initialCapacity) {
            capacity <<= 1;
        }

        this.loadFactor = DEFAULT_LOAD_FACTOR;
        this.keys = (K[]) new Object[capacity];
        this.values = (V[]) new Object[capacity];
        this.size = 0;
        this.resizeThreshold = (int) (this.capacity * this.loadFactor);
        if (this.resizeThreshold >= this.capacity) {
            this.resizeThreshold = this.capacity - 1; // Ensure threshold is less than capacity
        }
    }

    private int getInitialIndex(Object key) {
        return (key.hashCode() & 0x7FFFFFFF) & (capacity - 1);
    }

    /** Gets the value associated with the key, or null if not found. */
    public V get(K key) {
        if (key == null) {
            // Or throw? Collector shouldn't use null keys.
            throw new IllegalArgumentException("Key cannot be null");
        }

        int index = getInitialIndex(key);
        int probes = 0;
        while (probes < capacity) {
            K currentKey = keys[index];
            if (currentKey == null) {
                return null; // Not found
            }

            if (key.equals(currentKey)) {
                return values[index]; // Found
            }

            index = (index + 1) & (capacity - 1); // Linear probe
            probes++;
        }

        return null; // Not found after full probe
    }

    /** Puts the key-value pair. Updates if key exists, inserts if new. */
    public void put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Key/Value cannot be null");
        }

        // Check resize *before* probing if we might insert a new element
        if (size + 1 >= resizeThreshold && findSlotIndex(key) == -1) {
            resize();
        }
        int index = getInitialIndex(key);
        int probes = 0;
        while (probes < capacity) {
            K currentKey = keys[index];
            if (currentKey == null) { // Insert new
                keys[index] = key;
                values[index] = value;
                size++;
                return;
            }
            else if (key.equals(currentKey)) { // Update existing
                values[index] = value;
                return;
            }
            index = (index + 1) & (capacity - 1); // Linear probe
            probes++;
        }

        // Should only be reached if map is full and key wasn't found after probe
        // Defensive resize and retry
        if (size >= capacity) {
            resize();
            put(key, value); // Retry put after resize
            return;
        }

        throw new IllegalStateException("Map state error. Size: " + size + ", Capacity: " + capacity);
    }

    /** Helper to find a key's slot index, returns index or -1 if not found. */
    private int findSlotIndex(Object key) {
        int index = getInitialIndex(key);
        int probes = 0;
        while (probes < capacity) {
            K currentKey = keys[index];
            if (currentKey == null) {
                return -1; // Not found
            }
            if (key.equals(currentKey)) {
                return index; // Found
            }
            index = (index + 1) & (capacity - 1);
            probes++;
        }
        return -1; // Not found
    }

    /** Gets existing value or computes, puts, and returns new value. */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null)
            throw new NullPointerException();

        int index = getInitialIndex(key);
        int probes = 0;
        while (probes < capacity) {
            K currentKey = keys[index];
            if (currentKey == null) { // Key not found, compute and insert
                // Check resize *before* inserting
                if (size + 1 >= resizeThreshold) {
                    resize();
                    // Recalculate index after resize before inserting
                    return computeIfAbsent(key, mappingFunction); // Retry after resize
                }
                V newValue = mappingFunction.apply(key);
                if (newValue == null)
                    throw new NullPointerException("Mapping function returned null");
                keys[index] = key;
                values[index] = newValue;
                size++;
                return newValue;
            }
            else if (key.equals(currentKey)) { // Key found
                return values[index];
            }
            index = (index + 1) & (capacity - 1); // Linear probe
            probes++;
        }
        // Should not be reached if resize logic is correct.
        // Defensive resize and retry
        if (size >= capacity) {
            resize();
            return computeIfAbsent(key, mappingFunction); // Retry
        }

        throw new IllegalStateException("Map state error in computeIfAbsent.");
    }

    /** Iterates over map entries. */
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null)
            throw new NullPointerException();
        for (int i = 0; i < capacity; i++) {
            if (keys[i] != null) {
                action.accept(keys[i], values[i]);
            }
        }
    }

    /** Returns the number of key-value mappings. */
    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        int oldCapacity = this.capacity;
        K[] oldKeys = this.keys;
        V[] oldValues = this.values;

        int newCapacity = this.capacity << 1;
        // Check if doubling caused overflow or didn't increase capacity
        if (newCapacity <= 0 || newCapacity < oldCapacity) {
            // If already at max integer value, can't resize further
            if (oldCapacity == Integer.MAX_VALUE) {
                throw new IllegalStateException("Cannot resize, already at max capacity.");
            }

            // Try to set to maximum power of 2
            newCapacity = Integer.MAX_VALUE & -(1 << 30);

            // If that's still not enough, try absolute maximum integer value
            if (newCapacity <= oldCapacity) {
                newCapacity = Integer.MAX_VALUE;
            }

            // If we still can't increase capacity, give up
            if (newCapacity <= oldCapacity) {
                throw new IllegalStateException("Cannot resize, capacity overflow or already at max.");
            }
        }
        this.capacity = newCapacity;

        this.keys = (K[]) new Object[this.capacity];
        this.values = (V[]) new Object[this.capacity];
        this.size = 0; // Reset size, will be repopulated
        this.resizeThreshold = (int) (this.capacity * this.loadFactor);
        if (resizeThreshold <= 0 || resizeThreshold > this.capacity)
            resizeThreshold = this.capacity;
        if (this.resizeThreshold >= this.capacity) {
            this.resizeThreshold = this.capacity - 1;
        }

        for (int i = 0; i < oldCapacity; i++) {
            if (oldKeys[i] != null) {
                putNoResize(oldKeys[i], oldValues[i]);
            }
        }
    }

    private void putNoResize(K key, V value) { // Used only by resize
        int index = getInitialIndex(key);
        int probes = 0;
        while (probes < capacity) {
            K currentKey = keys[index];
            if (currentKey == null) {
                keys[index] = key;
                values[index] = value;
                size++;
                return;
            }
            index = (index + 1) & (capacity - 1);
            probes++;
        }
        throw new IllegalStateException("Resize failed to place an element: " + key);
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        this.keys = (K[]) new Object[this.capacity];
        this.values = (V[]) new Object[this.capacity];
        this.size = 0;
    }
}
