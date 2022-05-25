package envoy.mesh;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import envoy.AdminProvider;
import envoy.ConsumerProvider;
import envoy.Environment;
import envoy.ProducerProvider;
import envoy.UpstreamCluster;

public class ConsumerTest {

	private static final Logger LOG = LoggerFactory.getLogger(ConsumerTest.class);

	@Before
	public void setUp() throws Exception {

		for (UpstreamCluster cluster : Environment.CLUSTERS) {
			try (Admin admin = AdminProvider.makeClusterAdmin(cluster)) {

				Set<NewTopic> topics = cluster.getTopics().stream()
						.map(x -> new NewTopic(x, cluster.getPartitionCount(), (short) 1)).collect(Collectors.toSet());
				LOG.info("Creating topics: {}", topics);
				try {
					admin.createTopics(topics).all().get();
				} catch (ExecutionException e) {
				}
			}

		}

		try (Producer<byte[], byte[]> pr = ProducerProvider.makeClusterProducer(Environment.CLUSTER1)) {
			for (int i = 0; i < 3; ++i) {
				ProducerRecord<byte[], byte[]> r = new ProducerRecord<>("apples", 0, new byte[1024], new byte[1024]);
				RecordMetadata rm = pr.send(r).get();
				LOG.info("saved at {}", rm.offset());
			}
		}
		;
	}

	@Test
	public void test() {
		Consumer<byte[], byte[]> consumer = ConsumerProvider.makeMeshConsumer(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
				"1000");

		consumer.assign(Arrays.asList(new TopicPartition("apples", 0)));

		consumer.poll(Duration.ofSeconds(5));

		consumer.close();
	}

}
