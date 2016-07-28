package com.colobu.fiber;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.SuspendableRunnable;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//简单的线程和Fiber比较
public class Helloworld {

    static boolean isThread = true;

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
        if (isThread)
            Thread.sleep(1000);
        else
            Fiber.sleep(1000);
        return m;
    }

    //or define in META-INF/suspendables
    @Suspendable
    static String m3() {
        List l = Stream.of(1,2,3).filter(i -> i%2 == 0).collect(Collectors.toList());
        return l.toString();
    }

    static public void main(String[] args) throws ExecutionException, InterruptedException {
        int count = 100;

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

