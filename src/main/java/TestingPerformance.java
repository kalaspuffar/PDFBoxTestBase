import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class TestingPerformance {
    public static void main(String[] args) throws Exception {
        runScenario("Test", 5, 10);
    }

    public static final class Result {
        public final long wallNanos;
        public final long cpuNanos;
        public final long allocBytes;
        public final long heapBefore;
        public final long heapAfter;
        public final long gcCountDelta;
        public final long gcTimeMsDelta;

        Result(long wallNanos, long cpuNanos, long allocBytes,
               long heapBefore, long heapAfter, long gcCountDelta, long gcTimeMsDelta) {
            this.wallNanos = wallNanos;
            this.cpuNanos = cpuNanos;
            this.allocBytes = allocBytes;
            this.heapBefore = heapBefore;
            this.heapAfter = heapAfter;
            this.gcCountDelta = gcCountDelta;
            this.gcTimeMsDelta = gcTimeMsDelta;
        }

        @Override public String toString() {
            return String.format(
                    "wall_ms=%.3f cpu_ms=%.3f alloc_mb=%.3f heap_before_mb=%.3f heap_after_mb=%.3f heap_delta_mb=%.3f gc_count_delta=%d gc_time_ms_delta=%d",
                    wallNanos / 1e6,
                    cpuNanos / 1e6,
                    allocBytes / (1024.0 * 1024.0),
                    heapBefore / (1024.0 * 1024.0),
                    heapAfter / (1024.0 * 1024.0),
                    (heapAfter - heapBefore) / (1024.0 * 1024.0),
                    gcCountDelta,
                    gcTimeMsDelta
            );
        }
    }

    // --- Public API ---
    public static Result measure() throws Exception {
        // Baselines
        long tid = Thread.currentThread().getId();

        ThreadMXBean tmb = ManagementFactory.getThreadMXBean();
        if (tmb.isThreadCpuTimeSupported() && !tmb.isThreadCpuTimeEnabled()) {
            tmb.setThreadCpuTimeEnabled(true);
        }

        long cpuBefore = tmb.isCurrentThreadCpuTimeSupported() ? tmb.getCurrentThreadCpuTime() : -1L;
        long allocBefore = getAllocatedBytes(tid);

        long[] gcBefore = readGcTotals();

        Runtime rt = Runtime.getRuntime();
        long heapBefore = rt.totalMemory() - rt.freeMemory();

        long wallBefore = System.nanoTime();
        runTask();
        long wallAfter = System.nanoTime();

        long heapAfter = rt.totalMemory() - rt.freeMemory();
        long[] gcAfter = readGcTotals();

        long cpuAfter = (cpuBefore >= 0) ? tmb.getCurrentThreadCpuTime() : -1L;
        long allocAfter = getAllocatedBytes(tid);

        return new Result(
                wallAfter - wallBefore,
                (cpuBefore >= 0 && cpuAfter >= 0) ? (cpuAfter - cpuBefore) : -1L,
                (allocBefore >= 0 && allocAfter >= 0) ? (allocAfter - allocBefore) : -1L,
                heapBefore,
                heapAfter,
                gcAfter[0] - gcBefore[0],
                gcAfter[1] - gcBefore[1]
        );
    }

    private static void runTask() throws Exception {
        PDDocument doc = Loader.loadPDF(new File("before.pdf"));
        doc.save(new File("after.pdf"));
    }

    // --- GC totals across all collectors: [count, timeMs] ---
    private static long[] readGcTotals() {
        long count = 0;
        long timeMs = 0;
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc : gcs) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c >= 0) count += c;
            if (t >= 0) timeMs += t;
        }
        return new long[]{count, timeMs};
    }

    /**
     * Current-thread allocated bytes.
     * Works on HotSpot / OpenJDK (most common). Returns -1 if unavailable.
     */
    private static long getAllocatedBytes(long threadId) {
        try {
            ThreadMXBean tmb = ManagementFactory.getThreadMXBean();
            // com.sun.management is present on HotSpot/OpenJDK
            if (tmb instanceof com.sun.management.ThreadMXBean htmb) {
                if (htmb.isThreadAllocatedMemorySupported() && !htmb.isThreadAllocatedMemoryEnabled()) {
                    htmb.setThreadAllocatedMemoryEnabled(true);
                }
                if (htmb.isThreadAllocatedMemoryEnabled()) {
                    return htmb.getThreadAllocatedBytes(threadId);
                }
            }
        } catch (Throwable ignored) {
            // Not supported on this JVM
        }
        return -1L;
    }

    // Convenience: run warmup + N measured iterations and print median-ish (or all results)
    public static void runScenario(String name, int warmup, int iters) throws Exception {
        for (int i = 0; i < warmup; i++) runTask();

        System.out.println("== " + name + " ==");
        for (int i = 0; i < iters; i++) {
            Result r = measure();
            System.out.println("iter=" + i + " " + r);
        }
    }
}
