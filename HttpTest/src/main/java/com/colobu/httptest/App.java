package com.colobu.httptest;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.httpclient.FiberHttpClientBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class App {
    public static void main(String[] args) throws Exception {
        int t = Integer.parseInt(args[0]);
        int n = Integer.parseInt(args[1]);
        int sleep = Integer.parseInt(args[2]);

        if (t == 1)
            testHttpAgent(n, sleep);
        else if (t == 2)
            testHttpUrlConnection(n);
        else if (t == 3)
            testNetty(n);
        else if (t == 4)
            testComsat(n);
        else if (t == 5)
            testAHC(n);
        else if (t == 6)
            testApache(n);
    }


    static void testApache(int n) throws Exception {
        final BlockingQueue<CloseableHttpAsyncClient> connPool = new LinkedBlockingQueue<>();

        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger respNum = new AtomicInteger(0);
        AtomicInteger respOKNum = new AtomicInteger(0);

        final String url = "http://127.0.0.1:8999/";

        long t = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (int i = 0; i < n; i++) {
            pool.execute(() -> {
                try {
                    CloseableHttpAsyncClient httpclient = connPool.poll();
                    if (httpclient == null) {
                        httpclient = HttpAsyncClients.createDefault();
                        httpclient.start();
                    }

                    final  CloseableHttpAsyncClient client = httpclient;

                    final HttpGet request2 = new HttpGet(url);
                    httpclient.execute(request2, new FutureCallback<org.apache.http.HttpResponse>() {

                        public void completed(final org.apache.http.HttpResponse response2) {
                            int responseCode = response2.getStatusLine().getStatusCode();

                            respNum.incrementAndGet();
                            if (responseCode == 200)
                                respOKNum.incrementAndGet();


                            try {
                                connPool.put(client);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            latch.countDown();
                        }

                        public void failed(final Exception ex) {
                            try {
                                connPool.put(client);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            latch.countDown();
                        }

                        public void cancelled() {
                            try {
                                connPool.put(client);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            latch.countDown();
                        }

                    });

                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            });
        }


        latch.await(3, TimeUnit.MINUTES);
        t = System.nanoTime() - t;
        long t1 = t / 1000000;
        long rate = (n * 1000) / t1;


        System.out.println("\n\n===== Apache AsyncHttpClient  took: " + t / n + " ns =====");
        System.out.println("Sending Rate:" + rate + ", took: " + t1 + " ms");
        System.out.println("RespNum: " + respNum.get() + ", RespOKNum: " + respOKNum.get());
        System.out.println();

        pool.shutdownNow();

    }


    static void testAHC(int n) throws Exception{
        final BlockingQueue<AsyncHttpClient> connPool = new LinkedBlockingQueue<>();


        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger respNum = new AtomicInteger(0);
        AtomicInteger respOKNum = new AtomicInteger(0);

        final String url = "http://127.0.0.1:8999/";

        long t = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (int i = 0; i < n; i++) {
            pool.execute(() -> {
                try {
                    AsyncHttpClient asyncHttpClient = connPool.poll();
                    if (asyncHttpClient == null) {
                        asyncHttpClient = new DefaultAsyncHttpClient();
                    }


                    final AsyncHttpClient client = asyncHttpClient;

                    asyncHttpClient.prepareGet(url).execute(new AsyncCompletionHandler<Response>(){

                        @Override
                        public Response onCompleted(Response response) throws Exception{
                            latch.countDown();
                            int responseCode = response.getStatusCode();
                            respNum.incrementAndGet();
                            if (responseCode == 200)
                                respOKNum.incrementAndGet();

                            response.getResponseBody();


                            connPool.put(client);
                            return response;
                        }

                        @Override
                        public void onThrowable(Throwable t){
                            try {
                                connPool.put(client);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            latch.countDown();
                        }
                    });

                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            });
        }


        latch.await();
        t = System.nanoTime() - t;
        long t1 = t / 1000000;
        long rate = (n * 1000) / t1;


        System.out.println("\n\n===== AsyncHttpClient  took: " + t / n + " ns =====");
        System.out.println("Sending Rate:" + rate + ", took: " + t1 + " ms");
        System.out.println("RespNum: " + respNum.get() + ", RespOKNum: " + respOKNum.get());
        System.out.println();

        pool.shutdownNow();


    }

    static void testComsat(int n) throws Exception{
        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger respNum = new AtomicInteger(0);
        AtomicInteger respOKNum = new AtomicInteger(0);

        long t = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(200);

        final CloseableHttpClient client = FiberHttpClientBuilder.
                create(Runtime.getRuntime().availableProcessors()). // use 2 io threads
                setMaxConnPerRoute(20000).
                setMaxConnTotal(20000).build();

        for (int i = 0; i < n; i++) {
            new Fiber<String>("fiber-" + i, () -> {
                try {
                    client.execute(new HttpGet("http://localhost:8999"), resp -> {
                        int responseCode = resp.getStatusLine().getStatusCode();
                        respNum.incrementAndGet();
                        if (responseCode == 200)
                            respOKNum.incrementAndGet();

                        latch.countDown();
                        return EntityUtils.toString(resp.getEntity());
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }



        latch.await(3, TimeUnit.MINUTES);
        t = System.nanoTime() - t;
        long t1 = t / 1000000;
        long rate = (n * 1000) / t1;


        System.out.println("\n\n===== Comsat took: " + t / n + " ns =====");
        System.out.println("Sending Rate:" + rate + ", took: " + t1 + " ms");
        System.out.println("RespNum: " + respNum.get() + ", RespOKNum: " + respOKNum.get());
        System.out.println();

        pool.shutdownNow();
    }

    static void testNetty(int n) throws InterruptedException {
        final EventLoopGroup group = new NioEventLoopGroup(2000);
        final BlockingQueue<Channel> connPool = new LinkedBlockingQueue<>();
        try {

            final CountDownLatch latch = new CountDownLatch(n -10); //有时候不能完全返回
            final AtomicInteger respNum = new AtomicInteger(0);
            final AtomicInteger respOKNum = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(200);
            long t = System.nanoTime();
            for (int i = 0; i < n; i++) {
                //System.out.println("sent:" +  i + ", total: " + n);
                    Channel ch = connPool.poll();
                    if (ch == null || !ch.isActive()) {
                        ch = createNewChannel(group, connPool, latch, respNum, respOKNum);
                    }


                    // Prepare the HTTP request.
                    HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
                    request.headers().set(HttpHeaderNames.HOST, "127.0.0.1");
                    request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    request.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);


                    // Send the HTTP request.
                    ch.writeAndFlush(request);
            }


            latch.await();

            t = System.nanoTime() - t;
            long t1 = t / 1000000;
            long rate = (n * 1000) / t1;


            System.out.println("\n\n===== Netty took: " + t / n + " ns =====");
            System.out.println("Sending Rate:" + rate + ", took: " + t1 + " ms");
            System.out.println("RespNum: " + respNum.get() + ", RespOKNum: " + respOKNum.get());
            System.out.println();


            // Wait for the server to close the connection.
            //ch.closeFuture().sync();
        }finally {
            // Shut down executor threads to exit.
            group.shutdownGracefully();
        }
    }

    private static Channel createNewChannel(EventLoopGroup group,final BlockingQueue<Channel> connPool,
                                            final CountDownLatch latch,final AtomicInteger respNum,final AtomicInteger respOKNum) throws InterruptedException {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new SimpleChannelInboundHandler<HttpObject>() {
                            @Override
                            public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {


                                if (msg instanceof HttpResponse) {
                                    HttpResponse response = (HttpResponse) msg;

                                    respNum.incrementAndGet();
                                    //System.out.println(response.getStatus());

                                    if (response.status().code() == 200)
                                        respOKNum.incrementAndGet();

                                    if (HttpUtil.isTransferEncodingChunked(response)) {
                                        //System.out.println("CHUNKED CONTENT {");
                                    } else {
                                        //System.out.println("CONTENT {");
                                    }
                                }
                                if (msg instanceof HttpContent) {
                                    //int j = respNum.get();
                                    //System.out.println(j);


                                    HttpContent content = (HttpContent) msg;
                                    String body = content.content().toString();
                                    //System.out.println(body);

                                    if (content instanceof LastHttpContent) {
                                        //System.out.println("} END OF CONTENT");
                                        //ctx.close();
                                        latch.countDown();
                                    }

                                    try {
                                        connPool.put(ctx.channel());
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }


                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                cause.printStackTrace();
                                ctx.close();
                            }
                        });
                    }
                });

        // Make the connection attempt.
        return b.connect("127.0.0.1", 8999).sync().channel();
    }

    static void testHttpUrlConnection(int n) throws Exception {
        System.setProperty("http.maxConnections", "200");
        System.setProperty("sun.net.http.errorstream.enableBuffering", "true");

        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger respNum = new AtomicInteger(0);
        AtomicInteger respOKNum = new AtomicInteger(0);

        URL url = new URL("http://127.0.0.1:8999/");

        long t = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(200);

        for (int i = 0; i < n; i++) {
            pool.execute(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int responseCode = conn.getResponseCode();
                    respNum.incrementAndGet();
                    if (responseCode == 200)
                        respOKNum.incrementAndGet();

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    //StringBuffer response = new StringBuffer();
                    while ((inputLine = in.readLine()) != null) {
                        //response.append(inputLine);
                    }
                    in.close();

                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
                latch.countDown();
            });
        }


        latch.await(3, TimeUnit.MINUTES);
        t = System.nanoTime() - t;
        long t1 = t / 1000000;
        long rate = (n * 1000) / t1;


        System.out.println("\n\n===== HttpUrlConnection took: " + t / n + " ns =====");
        System.out.println("Sending Rate:" + rate + ", took: " + t1 + " ms");
        System.out.println("RespNum: " + respNum.get() + ", RespOKNum: " + respOKNum.get());
        System.out.println();

        pool.shutdownNow();

    }

    static void testHttpAgent(int n, int sleep) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(200);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger replyNum = new AtomicInteger(0);
        AtomicInteger replyOKNum = new AtomicInteger(0);
        AtomicInteger respNum = new AtomicInteger(0);
        AtomicInteger respOKNum = new AtomicInteger(0);


        int maxId = 10000 + n -1;

        final ResponseHandler handler = new ResponseHandler(latch, replyNum, respNum, replyOKNum, respOKNum,maxId);

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
                            pipeline.addLast(handler);
                        }
                    });




            // Start the connection attempt.
            Channel ch = b.connect("127.0.0.1", 8899).sync().channel();

            long t = System.nanoTime();

            for (int i = 0; i < n; i++) {
                ByteBuf buf = Unpooled.buffer();
                Msg.Request req = Msg.Request.newBuilder().setId(10000 + i)
                        .setMethod("GET").setUrl("http://127.0.0.1:8999/").build();

                byte[] bytes = req.toByteArray();
                buf.writeBytes(bytes);
                try {
                    ch.writeAndFlush(buf).sync();
                } catch (Exception ex) {
                    ch.close().sync();
                    ch = b.connect("127.0.0.1", 8899).sync().channel();
                }
                if (i % 1000 == 0 && sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
            long t1 = System.nanoTime() - t;
            t1 = t1 / 1000000; //ms
            long rate = (n * 1000) / t1;


            //send last request
//            Msg.Request req = Msg.Request.newBuilder().setId(-1)
//                    .setMethod("GET").setUrl("http://127.0.0.1:8999/").build();
//            byte[] bytes = req.toByteArray();
//            ByteBuf buf = Unpooled.buffer();
//            buf.writeBytes(bytes);
//            ch.writeAndFlush(buf).sync();

            latch.await();


            t = System.nanoTime() - t;
            System.out.println("\n\n===== HttpAgent took: " + t / n + " ns =====");
            System.out.println("Sending Rate:" + rate + ", took: " + t1 + " ms");
            System.out.println("ReplyNum: " + replyNum.get() + ", ReplyOKNum: " + replyOKNum.get());
            System.out.println("RespNum: " + respNum.get() + ", RespOKNum: " + respOKNum.get());
            System.out.println();

        } finally {
            group.shutdownGracefully();
        }
    }
}

@ChannelHandler.Sharable
//class ResponseHandler extends ChannelInboundHandlerAdapter {
class ResponseHandler extends  SimpleChannelInboundHandler<ByteBuf>{
    private CountDownLatch latch;
    private AtomicInteger replyNum;
    private AtomicInteger respNum;
    private AtomicInteger replyOKNum;
    private AtomicInteger respOKNum;
    private int maxId;

    public ResponseHandler(CountDownLatch latch, AtomicInteger replyNum, AtomicInteger respNum,
                           AtomicInteger replyOKNum, AtomicInteger respOKNum, int maxId) {
        this.latch = latch;
        this.replyNum = replyNum;
        this.respNum = respNum;
        this.replyOKNum = replyOKNum;
        this.respOKNum = respOKNum;
        this.maxId = maxId;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        if (buf.readableBytes() == 0) {
            buf.release();
            return;
        }

        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();

        Msg.Response resp = Msg.Response.parseFrom(bytes);
        if (resp.getId() == maxId) {
            latch.countDown();
        }

        if (resp.getIsReply() == true) {
            replyNum.incrementAndGet();
            if (resp.getStatusCode() == 200)
                replyOKNum.incrementAndGet();
        } else {
            respNum.incrementAndGet();
            if (resp.getStatusCode() == 200)
                respOKNum.incrementAndGet();
            else
                System.out.println(resp.getBody().toStringUtf8());
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println(cause.getMessage());
    }
}