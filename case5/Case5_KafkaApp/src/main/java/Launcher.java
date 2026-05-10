import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;

public class Launcher {

	private static final String BOOTSTRAP_SERVERS = "192.168.111.10:9092";
	private static final String INPUT_TOPIC = "driver-position";
	private static final String INTERMEDIATE_TOPIC = "group4-route-timing";
	private static final String OUTPUT_TOPIC = "delays";

	private static final int SIGNIFICANT_DELAY_SECONDS = 180; // > 3 min

	public static void main(String[] args) {

		Properties props = new Properties();
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, "group4-driver-delay-cep-" + Math.random());
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
		props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
		props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
		props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		final StreamsBuilder builder = new StreamsBuilder();

		// ---------------- Stage 1: DriverPosition -> RouteTiming ----------------
		KStream<String, String> positions = builder.stream(INPUT_TOPIC);

		KStream<String, String> timings = positions
				.peek((k, v) -> System.out.println("Stage 1 in <- key=" + k + " value=" + v))
				.mapValues((key, value) -> {
					Utils.GpsPos pos = Utils.extractCoordinates(value);
					if (pos == null) {
						System.err.println("Stage 1: could not parse position event: " + value);
						return null;
					}
					int delay = Utils.requestDelay(key, pos);
					String routeTiming = "id: " + key + ", delay: " + delay;
					System.out.println("Stage 1 -> " + routeTiming);
					return routeTiming;
				})
				.filter((k, v) -> v != null);

		timings.to(INTERMEDIATE_TOPIC);

		// ---------------- Stage 2: RouteTiming -> SignificantDelay --------------
	
		timings
				.filter((k, v) -> {
					Integer delay = Utils.extractDelay(v);
					return delay != null && delay > SIGNIFICANT_DELAY_SECONDS;
				})
				.peek((k, v) -> System.out.println("Stage 2 (significant) -> " + v))
				.to(OUTPUT_TOPIC);

		// ---------------- Build & run ------------------------------------------
		final Topology topology = builder.build();
		System.out.println(topology.describe());

		final KafkaStreams streams = new KafkaStreams(topology, props);
		final CountDownLatch latch = new CountDownLatch(1);

		Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
			@Override
			public void run() {
				streams.close();
				latch.countDown();
			}
		});

		try {
			streams.start();
			latch.await();
		} catch (Throwable e) {
			System.exit(1);
		}
		System.exit(0);
	}
}
