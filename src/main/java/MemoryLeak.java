import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class MemoryLeak {

    private static final String SETUP_SQL = """
        INSTALL httpfs;
        LOAD httpfs;
        SET session s3_region='%s';
        SET session s3_access_key_id='%s';
        SET session s3_secret_access_key='%s';
        SET session s3_session_token='%s';
    """;

    private static final String SELECT_SQL = """
        CREATE TABLE temp_%d AS 
        SELECT *
        FROM read_csv_auto('%s', ALL_VARCHAR=true);
    """;

    private static final String DROP_SQL = """
        DROP TABLE IF EXISTS temp_%d;
    """;

    private static final String MEMORY_LIMIT = "5GB";
    private static final int TASK_COUNT = 10;
    private static final int CONCURRENCY = 2;

    /**
     * A CSV file in S3 which is compressed with GZIP
     */
    private static final String S3_KEY = "s3://bucket/key.csv.gz"; // TODO: Replace with an S3 URI to compressed VSC file (~100MB in size)
    private static MemorySnapshot stats;


    public static void main(final String[] args) throws InterruptedException, SQLException {
        memoryStats();

        // Thread pool setup
        final ExecutorService threadPool = Executors.newFixedThreadPool(CONCURRENCY);

        // Create connection properties
        final Properties connectionProperties = new Properties();
        connectionProperties.setProperty("access_mode", "READ_WRITE");

        try (final Connection connection = DriverManager.getConnection("jdbc:duckdb:", connectionProperties);
             final Statement statement = connection.createStatement()) {
            statement.execute("SET memory_limit='" + MEMORY_LIMIT + "'");
        }

        // Sleep for a while to let diagnostic tools warmup
        System.out.println("Sleeping for 30 seconds to let container diagnostic tools warmup");
        Thread.sleep(Duration.ofSeconds(30).toMillis());

        System.out.println("Beginning tasks (" + TASK_COUNT + ") with concurrency (" + CONCURRENCY + ")");

        // Create workloads on the executor
        final List<CompletableFuture<Void>> completableFutures = IntStream.range(1, TASK_COUNT + 1) // tasks
            .mapToObj(taskNumber -> CompletableFuture.runAsync(() -> quack(connectionProperties, taskNumber), threadPool))
            .toList();

        // Wait for all to complete
        CompletableFuture.allOf(completableFutures.toArray(CompletableFuture[]::new))
            .whenComplete((v, e) -> threadPool.shutdown())
            .join();

        memoryStats();
        System.out.println("Sleeping for 10 minutes to keep container running to allow diagnostic analysis");

        // Sleep for a while to keep app running to monitor memory after workload completes
        Thread.sleep(Duration.ofMinutes(10).toMillis());

    }

    private static void memoryStats() {
        if (MemoryLeak.stats == null) {
            MemoryLeak.stats = MemorySnapshot.memoryStats();
            System.out.println(MemorySnapshot.memoryStats());
        } else {
            System.out.println("DIFF: \n" + MemorySnapshot.memoryStats().diff(MemoryLeak.stats));
        }
    }

    private static void quack(final Properties connectionProperties, final int taskNumber) {
        System.out.println("[" + taskNumber + "] > Opening Connection");
        try (final Connection connection = DriverManager.getConnection("jdbc:duckdb:", connectionProperties)) {

            // Setup the s3 variables
            final String s3Region = "us-east-1"; // TODO: replace with region of bucket
            final String s3AccessKeyId = ""; // TODO: replace with access key id
            final String s3SecretAccessKey = ""; // TODO: replace with secret access key
            final String s3SessionToken = ""; // TODO: replace with session token
            try (final Statement statement = connection.createStatement()) {

                System.out.println("[" + taskNumber + "] > Running Setup SQL");
                statement.execute(SETUP_SQL.formatted(s3Region, s3AccessKeyId, s3SecretAccessKey, s3SessionToken));

                System.out.println("[" + taskNumber + "] > Running SELECT SQL");
                statement.execute(SELECT_SQL.formatted(taskNumber, S3_KEY));

                System.out.println("[" + taskNumber + "] > Running DROP SQL");
                statement.execute(DROP_SQL.formatted(taskNumber));
            }

        } catch (final SQLException e) {
            System.err.println("[" + taskNumber + "] > Error: " + e.getMessage());
        } // connection and statement closed automatically with "try-with-resources"

        System.out.println("[" + taskNumber + "] > Closed Connection");
    }
}
