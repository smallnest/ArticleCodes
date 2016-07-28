package com.colobu.fiber;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.SuspendableRunnable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;


//测试如果将第三方的库的类instrument
public class Helloworld3 {

    @Suspendable
    static void m1() throws InterruptedException, SuspendExecution {
        String m = "m1";

        //System.out.println("m1 begin");
        m = m2();
        //System.out.println("m1 end");
        System.out.println(m);

    }

    static String m2() throws SuspendExecution, InterruptedException {
        String m = m3();
        return m;
    }

    //or define in META-INF/suspendables
    @Suspendable
    static String m3() {
        try {
            URL url = new URL("http://localhost:8999");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            return conn.getResponseMessage();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    static public void main(String[] args) throws ExecutionException, InterruptedException {
        int count = 10000;

        testFiber(count);
    }



    static void testFiber(int count) throws InterruptedException {
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

