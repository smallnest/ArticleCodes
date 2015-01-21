package com.colobu.unittest.kafka;

import java.util.Properties;
import java.util.Random;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

public class KafkaProducer{
	private Random rand = new Random();
	private ProducerConfig config;
	private Producer<String, String> producer;
	
	public KafkaProducer () {
		Properties props = new Properties();		 
		props.put("metadata.broker.list", "localhost:9092");
		props.put("serializer.class", "kafka.serializer.StringEncoder");
		props.put("partitioner.class", "example.producer.SimplePartitioner");
		props.put("request.required.acks", "1");
		 
		config = new ProducerConfig(props);	
	}
	
	public void start() {
		producer = new Producer<String, String>(config);
		
	}
	
	public void stop() {
		producer.close();
	}
	
	public void send(String message) {		 
		String ip = "key-192.168.2." + rand.nextInt(255);
		KeyedMessage<String, String> data = new KeyedMessage<String, String>("test", ip, message);		 
		producer.send(data);
	}

	public ProducerConfig getConfig() {
		return config;
	}

	public void setConfig(ProducerConfig config) {
		this.config = config;
	}
	
}
