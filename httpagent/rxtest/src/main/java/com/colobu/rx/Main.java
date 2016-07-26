package com.colobu.rx;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        //threadCheck2();


        int count = 100000;
        warmup(count / 100);

        testRxJavaWithoutBlocking(count);
        testRxJavaWithBlocking(count / 100);
        testRxJavaWithFlatMap(count / 100);
        testRxJavaWithParallel(count / 100);
        testRxJavaWithParallel2(count / 100);
    }

    public static void threadCheck() throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(5, new ThreadFactoryBuilder().setNameFormat("SubscribeOn-%d").build());

        Observable.just(0).subscribeOn(Schedulers.io()).map(i -> {
            System.out.println("A: " + Thread.currentThread().getName());
            return i;
        }).subscribeOn(Schedulers.from(es)).map(i -> {
            System.out.println("B: " + Thread.currentThread().getName());
            return i;
        }).observeOn(Schedulers.computation()).subscribe(statusCode -> {
            System.out.println("C: " + Thread.currentThread().getName());
        });
        System.in.read();
    }

    public static void threadCheck2() throws Exception {
        Observable.just(1,2,3).timeInterval()//.delay(1,TimeUnit.SECONDS)
                .subscribeOn(Schedulers.trampoline())
                .map(i -> {
                    System.out.println("map: " + Thread.currentThread().getName());
                    return i;
                })
                .subscribe(i -> {});

//        Observable.just(1,2,3)
//                .delay(1, TimeUnit.SECONDS)
//                .subscribeOn(Schedulers.newThread())
//                .map(i -> {
//                    System.out.println("map: " + Thread.currentThread().getName());
//                    return i;
//                })
//                .subscribe(i -> {});


        System.in.read();
    }

    public static void warmup(int count) throws Exception {

        CountDownLatch finishedLatch = new CountDownLatch(1);

        for (int k = 0; k < count; k++) {
            Observable.just(k).map(i -> {
                return 200;
            }).subscribeOn(Schedulers.io()).observeOn(Schedulers.computation()).subscribe(statusCode -> {

            }, error -> {

            }, () -> {
                finishedLatch.countDown();
            });
        }


        finishedLatch.await();
    }


    public static void testRxJavaWithoutBlocking(int count) throws Exception {

        CountDownLatch finishedLatch = new CountDownLatch(1);

        long t = System.nanoTime();
        Observable.range(0, count).map(i -> {
            //System.out.println("A:" + Thread.currentThread().getName());
            return 200;
        }).subscribeOn(Schedulers.io()).observeOn(Schedulers.computation()).subscribe(statusCode -> {
            //System.out.println("B:" + Thread.currentThread().getName());
        }, error -> {

        }, () -> {
            finishedLatch.countDown();
        });

        finishedLatch.await();
        t = (System.nanoTime() - t) / 1000000; //ms

        System.out.println("RxJavaWithoutBlocking TPS: " + count * 1000 / t);
    }

    public static void testRxJavaWithBlocking(int count) throws Exception {
        URL url = new URL("http://127.0.0.1:8999/");
        CountDownLatch finishedLatch = new CountDownLatch(1);

        long t = System.nanoTime();
        Observable.range(0, count).map(i -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                int responseCode = conn.getResponseCode();

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    //response.append(inputLine);
                }
                in.close();


                return responseCode;
            } catch (Exception ex) {
                return -1;
            }

        }).subscribeOn(Schedulers.io()).observeOn(Schedulers.computation()).subscribe(statusCode -> {
        }, error -> {

        }, () -> {
            finishedLatch.countDown();
        });

        finishedLatch.await();
        t = (System.nanoTime() - t) / 1000000; //ms

        System.out.println("RxJavaWithBlocking TPS: " + count * 1000 / t);
    }


    public static void testRxJavaWithFlatMap(int count) throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(200, new ThreadFactoryBuilder().setNameFormat("SubscribeOn-%d").build());

        URL url = new URL("http://127.0.0.1:8999/");
        CountDownLatch finishedLatch = new CountDownLatch(1);

        long t = System.nanoTime();
        Observable.range(0, count).subscribeOn(Schedulers.io()).flatMap(i -> {
                    //System.out.println("A: " + Thread.currentThread().getName());
                    return Observable.just(i).subscribeOn(Schedulers.from(es)).map(v -> {
                                //System.out.println("B: " + Thread.currentThread().getName());
                                try {
                                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestMethod("GET");
                                    int responseCode = conn.getResponseCode();

                                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                    String inputLine;
                                    while ((inputLine = in.readLine()) != null) {
                                        //response.append(inputLine);
                                    }
                                    in.close();


                                    return responseCode;
                                } catch (Exception ex) {
                                    return -1;
                                }
                            }
                    );
                }

        ).observeOn(Schedulers.computation()).subscribe(statusCode -> {
            //System.out.println("C: " + Thread.currentThread().getName());
        }, error -> {

        }, () -> {
            finishedLatch.countDown();
        });

        finishedLatch.await();
        t = (System.nanoTime() - t) / 1000000; //ms

        System.out.println("RxJavaWithFlatMap TPS: " + count * 1000 / t);
        es.shutdownNow();
    }


    public static void testRxJavaWithParallel(int count) throws Exception {
        URL url = new URL("http://127.0.0.1:8999/");
        CountDownLatch finishedLatch = new CountDownLatch(count);

        long t = System.nanoTime();
        for (int k = 0; k < count; k++) {
            Observable.just(k).map(i -> {
                //System.out.println("A: " + Thread.currentThread().getName());
                try {
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int responseCode = conn.getResponseCode();

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        //response.append(inputLine);
                    }
                    in.close();


                    return responseCode;
                } catch (Exception ex) {
                    return -1;
                }

            }).subscribeOn(Schedulers.io()).observeOn(Schedulers.computation()).subscribe(statusCode -> {
            }, error -> {

            }, () -> {
                finishedLatch.countDown();
            });
        }


        finishedLatch.await();
        t = (System.nanoTime() - t) / 1000000; //ms

        System.out.println("RxJavaWithParallel TPS: " + count * 1000 / t);

    }

    public static void testRxJavaWithParallel2(int count) throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(200, new ThreadFactoryBuilder().setNameFormat("SubscribeOn-%d").build());

        URL url = new URL("http://127.0.0.1:8999/");
        CountDownLatch finishedLatch = new CountDownLatch(count);

        long t = System.nanoTime();
        for (int k = 0; k < count; k++) {
            Observable.just(k).map(i -> {
                //System.out.println("A: " + Thread.currentThread().getName());
                try {
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int responseCode = conn.getResponseCode();

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        //response.append(inputLine);
                    }
                    in.close();


                    return responseCode;
                } catch (Exception ex) {
                    return -1;
                }

            }).subscribeOn(Schedulers.from(es)).observeOn(Schedulers.computation()).subscribe(statusCode -> {
            }, error -> {

            }, () -> {
                finishedLatch.countDown();
            });
        }


        finishedLatch.await();
        t = (System.nanoTime() - t) / 1000000; //ms

        System.out.println("RxJavaWithParallel2 TPS: " + count * 1000 / t);
        es.shutdownNow();
    }


}
