import java.io.Closeable;
import java.io.IOException;
import java.util.*;
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

//        In the example below, the top three are ages 10, 15 and 12
//        return new String[]{
//                String.format(OUTPUT_FORMAT, 1, 10, 38),
//                String.format(OUTPUT_FORMAT, 2, 15, 35),
//                String.format(OUTPUT_FORMAT, 3, 12, 30)
//        };

        throw new UnsupportedOperationException();
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

                // Skip invalid ages, continue processing
                if (age == null || age < 0) {
                    continue;
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
     * Ties at the same count share the same rank; next rank continues unbroken.
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

            for (Integer age : ages) {
                result.add(String.format(OUTPUT_FORMAT, rank, age, count));
            }

            // Only include up to 3 distinct count tiers (but all ties at tier 3 boundary)
            if (rank >= 3) {
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
