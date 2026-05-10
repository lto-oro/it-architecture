import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class Launcher {

	private static final String BROKER = "192.168.111.10:9092";
	private static final String DRIVER_POSITION_TOPIC = "driver-position";
	private static final String ROUTE_TIMING_TOPIC = "group4-route-timing";
	private static final String DELAYS_TOPIC = "delays";

	public static void main(String[] args) throws InterruptedException {
		StageRunner stage1 = new StageRunner("stage1-route-timing", DRIVER_POSITION_TOPIC, Launcher::mapPosition);
		StageRunner stage2 = new StageRunner("stage2-significant-delay", ROUTE_TIMING_TOPIC, Launcher::mapDelay);

		Thread stage1Thread = new Thread(stage1, "group4-stage1-route-timing");
		Thread stage2Thread = new Thread(stage2, "group4-stage2-significant-delay");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			stage1.stop();
			stage2.stop();
			joinQuietly(stage1Thread);
			joinQuietly(stage2Thread);
		}, "group4-shutdown"));

		System.out.println("Group 4 Case 5 Kafka app starting.");
		System.out.println("Stage 1: " + DRIVER_POSITION_TOPIC + " -> " + ROUTE_TIMING_TOPIC);
		System.out.println("Stage 2: " + ROUTE_TIMING_TOPIC + " -> " + DELAYS_TOPIC);
		System.out.println("No Consumer Groups are used; consumers are assigned directly and seek to topic end.");

		stage1Thread.start();
		stage2Thread.start();

		stage1Thread.join();
		stage2Thread.join();
	}

	private static ProducerRecord<String, String> mapPosition(ConsumerRecord<String, String> record) {
		DelayProcessing.RouteTiming routeTiming = DelayProcessing.toRouteTiming(
				record.key(),
				record.value(),
				Utils::requestDelay);

		if (routeTiming == null) {
			System.out.println("Stage 1 skipped invalid position event: " + record.value());
			return null;
		}

		System.out.println("Stage 1 produced: " + routeTiming.message);
		return new ProducerRecord<>(ROUTE_TIMING_TOPIC, routeTiming.id, routeTiming.message);
	}

	private static ProducerRecord<String, String> mapDelay(ConsumerRecord<String, String> record) {
		String dashboardEvent = DelayProcessing.toSignificantDelay(record.value());
		if (dashboardEvent == null) {
			return null;
		}

		System.out.println("Stage 2 produced dashboard event: " + dashboardEvent);
		return new ProducerRecord<>(DELAYS_TOPIC, record.key(), dashboardEvent);
	}

	private static void joinQuietly(Thread thread) {
		try {
			thread.join(5000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private interface EventMapper {
		ProducerRecord<String, String> map(ConsumerRecord<String, String> record);
	}

	private static final class StageRunner implements Runnable {
		private final String name;
		private final String inputTopic;
		private final EventMapper mapper;
		private volatile boolean running = true;
		private volatile KafkaConsumer<String, String> consumer;

		StageRunner(String name, String inputTopic, EventMapper mapper) {
			this.name = name;
			this.inputTopic = inputTopic;
			this.mapper = mapper;
		}

		void stop() {
			running = false;
			KafkaConsumer<String, String> currentConsumer = consumer;
			if (currentConsumer != null) {
				currentConsumer.wakeup();
			}
		}

		@Override
		public void run() {
			try (KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(consumerProps(name));
					KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps(name))) {
				consumer = kafkaConsumer;
				assignToTopicEnd(kafkaConsumer, inputTopic);

				while (running) {
					ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
					for (ConsumerRecord<String, String> record : records) {
						ProducerRecord<String, String> output = mapper.map(record);
						if (output != null) {
							producer.send(output);
							producer.flush();
						}
					}
				}
			} catch (WakeupException e) {
				if (running) {
					throw e;
				}
			} catch (Exception e) {
				System.err.println(name + " stopped because of an error.");
				e.printStackTrace();
			}
		}

		private void assignToTopicEnd(KafkaConsumer<String, String> kafkaConsumer, String topic) {
			List<PartitionInfo> partitions = waitForPartitions(kafkaConsumer, topic);

			List<TopicPartition> topicPartitions = new ArrayList<>();
			for (PartitionInfo partition : partitions) {
				topicPartitions.add(new TopicPartition(partition.topic(), partition.partition()));
			}

			kafkaConsumer.assign(topicPartitions);
			kafkaConsumer.seekToEnd(topicPartitions);
			System.out.println(name + " assigned " + topicPartitions + " at topic end.");
		}

		private List<PartitionInfo> waitForPartitions(KafkaConsumer<String, String> kafkaConsumer, String topic) {
			while (running) {
				List<PartitionInfo> partitions = kafkaConsumer.partitionsFor(topic, Duration.ofSeconds(10));
				if (partitions != null && !partitions.isEmpty()) {
					return partitions;
				}
				System.out.println(name + " waiting for topic " + topic + "...");
			}
			return new ArrayList<>();
		}
	}

	private static Properties consumerProps(String stageName) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER);
		props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId(stageName + "-consumer"));
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
		return props;
	}

	private static Properties producerProps(String stageName) {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER);
		props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId(stageName + "-producer"));
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.ACKS_CONFIG, "1");
		return props;
	}

	private static String clientId(String part) {
		return "group4-case5-" + part + "-" + System.nanoTime();
	}
}
