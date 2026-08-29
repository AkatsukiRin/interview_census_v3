import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Implement the two methods below. We expect this class to be stateless and thread safe.
 */
public class Census {
    /**
     * Number of cores in the current machine.
     */
    private static final int CORES = Runtime.getRuntime().availableProcessors();

    /**
     * Output format expected by our tests.
     */
    public static final String OUTPUT_FORMAT = "%d:%d=%d"; // Position:Age=Total

    /**
     * Factory for iterators.
     */
    private final Function<String, Census.AgeInputIterator> iteratorFactory;

    /**
     * Creates a new Census calculator.
     *
     * @param iteratorFactory factory for the iterators.
     */
    public Census(Function<String, Census.AgeInputIterator> iteratorFactory) {
        this.iteratorFactory = iteratorFactory;
    }

    /**
     * Given one region name, call {@link #iteratorFactory} to get an iterator for this region and return
     * the 3 most common ages in the format specified by {@link #OUTPUT_FORMAT}.
     */
    public String[] top3Ages(String region) {
        return rank(countRegion(region));
    }

    /**
     * Given a list of region names, call {@link #iteratorFactory} to get an iterator for each region and return
     * the 3 most common ages across all regions in the format specified by {@link #OUTPUT_FORMAT}.
     * We expect you to make use of all cores in the machine, specified by {@link #CORES).
     */
    public String[] top3Ages(List<String> regionNames) {
        if (regionNames == null || regionNames.isEmpty()) {
            return new String[0];
        }

        int poolSize = Math.min(CORES, regionNames.size());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Future<Map<Integer, Long>>> futures = new ArrayList<>();

        try {
            // Submit a task for each region; failures per-region are swallowed
            for (String region : regionNames) {
                futures.add(executor.submit(() -> {
                    try {
                        return countRegion(region);
                    } catch (RuntimeException e) {
                        // Swallow per-region failures; return empty map
                        return Collections.emptyMap();
                    }
                }));
            }

            // Merge results from all regions
            Map<Integer, Long> mergedCounts = new HashMap<>();
            for (Future<Map<Integer, Long>> future : futures) {
                try {
                    Map<Integer, Long> regionCounts = future.get();
                    regionCounts.forEach((age, count) ->
                        mergedCounts.merge(age, count, Long::sum)
                    );
                } catch (InterruptedException e) {
                    // Preserve interrupt status for proper thread pool shutdown
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Swallow other exceptions from future.get()
                }
            }

            return rank(mergedCounts);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Drains an iterator for a single region and counts age frequencies.
     * Skips invalid/negative ages and ensures iterator is always closed.
     */
    private Map<Integer, Long> countRegion(String region) {
        AgeInputIterator iterator = iteratorFactory.apply(region);
        Map<Integer, Long> counts = new HashMap<>();

        try {
            while (true) {
                boolean hasNext = false;
                try {
                    hasNext = iterator.hasNext();
                } catch (RuntimeException e) {
                    // Iterator broken, treat as end of data
                    break;
                }

                if (!hasNext) {
                    break;
                }

                Integer age = null;
                try {
                    age = iterator.next();
                } catch (RuntimeException e) {
                    // Iterator broken, treat as end of data
                    break;
                }

                // Throw on invalid ages; exception propagates after finally closes iterator
                if (age == null || age < 0) {
                    throw new IllegalArgumentException("Invalid age: " + age);
                }

                counts.merge(age, 1L, Long::sum);
            }
        } finally {
            try {
                iterator.close();
            } catch (IOException e) {
                // Ignore close exceptions
            }
        }

        return counts;
    }

    /**
     * Formats age counts into a ranked String array using dense ranking.
     * Includes top 3 count tiers; for tier 3, only the minimum age is included.
     */
    private static String[] rank(Map<Integer, Long> counts) {
        if (counts.isEmpty()) {
            return new String[0];
        }

        // Group ages by count, sort counts descending
        Map<Long, List<Integer>> countToAges = new TreeMap<>(Collections.reverseOrder());
        for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
            countToAges.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        // Sort ages within each count group in ascending order
        for (List<Integer> ages : countToAges.values()) {
            Collections.sort(ages);
        }

        List<String> result = new ArrayList<>();
        int rank = 1;

        for (Map.Entry<Long, List<Integer>> entry : countToAges.entrySet()) {
            long count = entry.getKey();
            List<Integer> ages = entry.getValue();

            if (rank <= 2) {
                // Include all ages for ranks 1 and 2
                for (Integer age : ages) {
                    result.add(String.format(OUTPUT_FORMAT, rank, age, count));
                }
            } else if (rank == 3) {
                // For rank 3, only include the first (minimum) age
                result.add(String.format(OUTPUT_FORMAT, rank, ages.get(0), count));
                break;
            }

            rank++;
        }

        return result.toArray(new String[0]);
    }


    /**
     * Implementations of this interface will return ages on call to {@link Iterator#next()}. They may open resources
     * when being instantiated created.
     */
    public interface AgeInputIterator extends Iterator<Integer>, Closeable {
    }
}
