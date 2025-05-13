package dev.vishrayne.onebrc;

import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SegmentKeyInterner {
    // Global interner: The ultimate source of canonical SegmentKey instances.
    // Tuned for potential high concurrency and expected number of unique station names.
    private static final ConcurrentHashMap<SegmentKey, SegmentKey> GLOBAL_INTERNER = new ConcurrentHashMap<>(16384, 0.75f,
            Runtime.getRuntime().availableProcessors() * 2);

    // // Thread-local cache: Each thread gets its own HashMap.
    // // This map stores SegmentKey (based on local slice) -> canonical SegmentKey (from GLOBAL_INTERNER).
    // private static final ThreadLocal<Map<SegmentKey, SegmentKey>> THREAD_LOCAL_CACHE = ThreadLocal.withInitial(() -> new HashMap<>(128)); // Modest initial capacity for thread-local map
    //
    // // Eviction threshold for the thread-local cache.
    // // If a thread processes an unusually diverse set of new keys, its local cache might grow.
    // // This provides a basic mechanism to control its size.
    // private static final int THREAD_LOCAL_CACHE_EVICTION_THRESHOLD = 256; // Tune as needed

    public static SegmentKey simpleIntern(MemorySegment stationNameSlice) {
        SegmentKey tempKey = new SegmentKey(stationNameSlice);
        return GLOBAL_INTERNER.computeIfAbsent(tempKey, k -> k);
    }
    // /**
    // * Retrieves or creates a canonical SegmentKey for the given MemorySegment slice.
    // * It first checks a thread-local cache, then falls back to a global concurrent cache.
    // *
    // * @param stationNameSlice The MemorySegment representing the station name.
    // * @return The canonical (interned) SegmentKey.
    // */
    // public static SegmentKey threadLocalIntern(MemorySegment stationNameSlice) {
    // // 1. Create a temporary SegmentKey. This object is used for lookups
    // // in both the thread-local and global caches. Its hashCode() and equals()
    // // methods (based on content) will be invoked.
    // SegmentKey tempKeyForLookup = new SegmentKey(stationNameSlice);
    //
    // // 2. Check the thread-local cache.
    // Map<SegmentKey, SegmentKey> localCache = THREAD_LOCAL_CACHE.get();
    // SegmentKey canonicalKey = localCache.get(tempKeyForLookup);
    //
    // if (canonicalKey != null) {
    // // Found in thread-local cache, return the (already globally interned) canonical key.
    // return canonicalKey;
    // }
    //
    // // 3. Not found in thread-local cache, so consult the global interner.
    // // computeIfAbsent ensures atomic check-and-put.
    // // The lambda (k -> k) means if tempKeyForLookup is not found (by equals),
    // // tempKeyForLookup itself will be stored as the canonical instance.
    // canonicalKey = GLOBAL_INTERNER.computeIfAbsent(tempKeyForLookup, k -> k);
    //
    // // 4. Store the mapping in the thread-local cache.
    // // Check size *before* putting to prevent exceeding threshold temporarily if it matters.
    // if (localCache.size() >= THREAD_LOCAL_CACHE_EVICTION_THRESHOLD) {
    // localCache.clear(); // Evict if threshold is met or exceeded
    // }
    // localCache.put(tempKeyForLookup, canonicalKey);
    //
    // return canonicalKey;
    // }
}
