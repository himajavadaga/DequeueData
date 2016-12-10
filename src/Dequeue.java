import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class Dequeue {

	private final static String QUEUE_NAME = "postToElasticSearch";
	private final static String HOST_NAME = "localhost";
	private final static String INDEX_NAME = "test";
	private ConnectionFactory factory = null;
	private Connection connection = null;
	private Channel channel = null;

	public boolean init() {
		boolean isInitiated = false;
		try {
			factory = new ConnectionFactory();
			factory.setHost(HOST_NAME);
			connection = factory.newConnection();
			channel = connection.createChannel();
			isInitiated = true;
		} catch (IOException | TimeoutException e) {
			// TODO Auto-generated catch block
			System.err.println("Error initiating the queue:");
			e.printStackTrace();
		}
		return isInitiated;
	}

	public boolean declareMessageQueue(String queueName) {
		boolean isQueueDeclared = false;
		try {
			channel.queueDeclare(queueName, false, false, false, null);
			isQueueDeclared = true;
		} catch (IOException e) {
			System.err.println("Error Declaring Queue:");
			e.printStackTrace();
		}
		return isQueueDeclared;
	}

	public void readFromQueue(String queueName, Client client, String indexName) {
		try {
			Consumer consumer = new DefaultConsumer(channel) {
				@Override
				public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
						byte[] body) throws IOException {
					String message = new String(body, "UTF-8");
					System.out.println(" [x] Received '" + message + "'");
					postToElasticSearch(client, indexName, message);
				}
			};
			channel.basicConsume(queueName, true, consumer);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public TransportClient getClient() {
		TransportClient transportClient = null;
		try {
			transportClient = new PreBuiltTransportClient(Settings.EMPTY)
					.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(HOST_NAME), 9300));
		} catch (Exception e) {
			System.out.println("Error Creating Client:" + e.getMessage());
			e.printStackTrace();
		}
		return transportClient;
	}

	public void createIndexIfNotExist(Client client, String indexName) throws InterruptedException, ExecutionException {
		IndicesExistsRequestBuilder indicesExistRequestBuilder = client.admin().indices().prepareExists(indexName);

		IndicesExistsResponse response = indicesExistRequestBuilder.execute().get();
		if (!response.isExists()) {
			CreateIndexRequestBuilder createIndexRequestBuilder = client.admin().indices().prepareCreate(indexName);
			CreateIndexResponse createIndexResponse = createIndexRequestBuilder.execute().get();
			if (createIndexResponse.isAcknowledged()) {
				System.out.println("Index Created!\n" + createIndexResponse.toString());
			}
			System.out.println("Index Created!");
		}
	}

	public void postToElasticSearch(Client client, String indexName, String document) {

		try {
			IndexRequestBuilder indexRequestBuilder = client.prepareIndex(indexName, "info").setSource(document);
			IndexResponse response = indexRequestBuilder.execute().get(30, TimeUnit.SECONDS);
			System.out.println(
					"Status:" + response.status() + ", Result:" + response.getResult() + ", Id:" + response.getId());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void main(String[] args) throws InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		Dequeue dequeue = new Dequeue();
		if (dequeue.init()) {
			if (dequeue.declareMessageQueue(QUEUE_NAME)) {
				TransportClient elasticSearchClient = dequeue.getClient();
				if (elasticSearchClient != null) {
					dequeue.createIndexIfNotExist(elasticSearchClient, INDEX_NAME);
					dequeue.readFromQueue(QUEUE_NAME, elasticSearchClient, INDEX_NAME);
				} else {
					System.out.println("Error!!!!");
				}
			}
		}
	}

}
