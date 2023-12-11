public record MemorySnapshot(long totalMemory, long freeMemory, long usedMemory, long maxMemory) {
    @Override
    public String toString() {
        int kb = 1024;
        int mb = 1024 * kb;

        String out = "***** Heap utilization statistics [KB] *****\n";
        out += "Total Memory: %,d kb, %,d mb\n".formatted(totalMemory / kb, totalMemory / mb);
        out += "Free Memory: %,d kb, %,d mb\n".formatted(freeMemory / kb, freeMemory / mb);
        out += "Used Memory: %,d kb, %,d mb\n".formatted(usedMemory / kb, usedMemory / mb);
        out += "Max Memory: %,d kb, %,d mb\n".formatted(maxMemory / kb, maxMemory / mb);

        return out;
    }

    public MemorySnapshot diff(MemorySnapshot other) {
        return new MemorySnapshot(
                totalMemory - other.totalMemory,
                freeMemory - other.freeMemory,
                usedMemory - other.usedMemory,
                maxMemory - other.maxMemory
        );
    }

    public static MemorySnapshot memoryStats() {
        // get Runtime instance
        Runtime instance = Runtime.getRuntime();

        return new MemorySnapshot(
                instance.totalMemory(),
                instance.freeMemory(),
                (instance.totalMemory() - instance.freeMemory()),
                instance.maxMemory()
        );
    }
}
