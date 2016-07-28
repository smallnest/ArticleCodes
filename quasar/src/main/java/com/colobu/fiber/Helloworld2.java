package com.colobu.fiber;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.fibers.httpclient.FiberHttpClientBuilder;
import co.paralleluniverse.strands.SuspendableRunnable;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//一个模拟实际情况的例子, 以comsat-httpclient做客户端
public class Helloworld2 {

    static boolean isThread = true;
    final static CloseableHttpClient client = FiberHttpClientBuilder.
            create(Runtime.getRuntime().availableProcessors()). // use 2 io threads
            setMaxConnPerRoute(2000).
            setMaxConnTotal(2000).build();

    @Suspendable
    static void m1() throws InterruptedException, SuspendExecution {
        String m = "m1";

        //System.out.println("m1 begin");
        m = m2();
        //System.out.println("m1 end");
        //System.out.println(m);

    }

    static String m2() throws SuspendExecution, InterruptedException {
        String m = m3();
        return m;
    }

    //https://groups.google.com/forum/#!searchin/quasar-pulsar-user/Uninstrumented$20methods$20/quasar-pulsar-user/sySs7b3ddiM/fV0sAMz0CQAJ
    @Suspendable
    static String m3() {
        try {
            String response = client.execute(new HttpGet("http://localhost:8999"), new BasicResponseHandler());
            return response;
        } catch (IOException e) {
            e.printStackTrace();
        }


        return "";
    }

    static public void main(String[] args) throws ExecutionException, InterruptedException {
        int count = 100000;

        //testThreadpool(count);
        testFiber(count);

    }

    static void testThreadpool(int count) throws InterruptedException {
        isThread = true;

        final CountDownLatch latch = new CountDownLatch(count);
        ExecutorService es = Executors.newFixedThreadPool(200);

        long t = System.currentTimeMillis();
        for (int i =0; i< count; i++) {
            es.submit(() -> {
                try {
                    m1();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (SuspendExecution suspendExecution) {
                    suspendExecution.printStackTrace();
                }
                latch.countDown();
            });
        }

        latch.await();
        t = System.currentTimeMillis() - t;

        System.out.println("thread pool took: " + t);

        es.shutdownNow();
    }

    static void testFiber(int count) throws InterruptedException {
        isThread = false;

        final CountDownLatch latch = new CountDownLatch(count);

        long t = System.currentTimeMillis();
        for (int i =0; i< count; i++) {
            new Fiber<Void>("Caller", new SuspendableRunnable() {

                @Override
                public void run() throws SuspendExecution, InterruptedException {
                    m1();
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        t = System.currentTimeMillis() - t;

        System.out.println("fiber took: " + t);
    }
}

