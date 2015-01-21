package com.colobu.unittest.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.WatchedEvent;

public class CuratorApp {
	private String path = "/com/colobu/test";
	public String setAndGetData(String zookeeperConnectionString, String payload) {
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
		CuratorFramework client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, retryPolicy);
		client.start();
		
		try {
			ZKPaths.mkdirs(client.getZookeeperClient().getZooKeeper(), path);
			client.setData().forPath(path, payload.getBytes());
			
			
			return new String(client.getData().forPath(path));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public void watch(String zookeeperConnectionString) throws Exception {
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
		final CuratorFramework client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, retryPolicy);
		client.start();
		client.getCuratorListenable().addListener(new CuratorListener() {
			
			public void eventReceived(CuratorFramework client, CuratorEvent event) throws Exception {
				System.err.println("CuratorListenable:" + event.getType() + ":" + new String(event.getData()));
			}
		});
		ZKPaths.mkdirs(client.getZookeeperClient().getZooKeeper(), path);
		
		new Thread() {
			@Override
			public void run() {
				try {
					client.getData().usingWatcher(new CuratorWatcher() {						
						public void process(WatchedEvent event) throws Exception {
							System.out.println("Watcher:" + event.getType() + ":" + new String(event.getPath()));
						}
					}).forPath(path);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}}.start();
		
		
		for(int i =0; i < 10; i++) {
			client.setData().forPath(path, (System.currentTimeMillis()+"").getBytes());
			Thread.sleep(1000);
		}
	}

}
