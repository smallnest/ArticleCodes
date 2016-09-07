package main

import (
	"flag"
	"fmt"
	"reflect"
	"sync"
	"sync/atomic"
	"time"

	"github.com/montanaflynn/stats"
	"github.com/smallnest/rpcx"
	"github.com/smallnest/rpcx/codec"
)

var concurrency = flag.Int("c", 1, "concurrency")
var total = flag.Int("n", 1, "total requests for all clients")
var host = flag.String("s", "127.0.0.1:8972", "server ip and port")
var sharedClientNum = flag.Int("r", 500, "count of rpcx.Client")

func main() {
	flag.Parse()
	n := *concurrency
	m := *total / n

	fmt.Printf("concurrency: %d\nrequests per client: %d\n\n", n, m)

	serviceMethodName := "Hello.Say"
	args := prepareArgs()

	b := make([]byte, 1024*1024)
	i, _ := args.MarshalTo(b)
	fmt.Printf("message size: %d bytes\n\n", i)

	var wg sync.WaitGroup
	wg.Add(n * m)

	var trans uint64
	var transOK uint64

	d := make([][]int64, n, n)

	clients := prepareClients(*host, *sharedClientNum, serviceMethodName, args)

	//it contains warmup time but we can ignore it
	totalT := time.Now().UnixNano()
	for i := 0; i < n; i++ {
		dt := make([]int64, 0, m)
		d = append(d, dt)

		go func(i int) {

			var reply BenchmarkMessage

			for j := 0; j < m; j++ {
				clientId := (i*m + j) % *sharedClientNum
				t := time.Now().UnixNano()
				err := clients[clientId].Call(serviceMethodName, args, &reply)
				t = time.Now().UnixNano() - t

				d[i] = append(d[i], t)

				if err == nil && reply.Field1 == "OK" {
					atomic.AddUint64(&transOK, 1)
				}

				atomic.AddUint64(&trans, 1)
				wg.Done()
			}
		}(i)

	}

	wg.Wait()
	totalT = time.Now().UnixNano() - totalT
	totalT = totalT / 1000000
	fmt.Printf("took %d ms for %d requests", totalT, n*m)

	totalD := make([]int64, 0, n*m)
	for _, k := range d {
		totalD = append(totalD, k...)
	}
	totalD2 := make([]float64, 0, n*m)
	for _, k := range totalD {
		totalD2 = append(totalD2, float64(k))
	}

	mean, _ := stats.Mean(totalD2)
	median, _ := stats.Median(totalD2)
	max, _ := stats.Max(totalD2)
	min, _ := stats.Min(totalD2)
	p99, _ := stats.Percentile(totalD2, 99.9)

	fmt.Printf("sent     requests    : %d\n", n*m)
	fmt.Printf("received requests    : %d\n", atomic.LoadUint64(&trans))
	fmt.Printf("received requests_OK : %d\n", atomic.LoadUint64(&transOK))
	fmt.Printf("throughput  (TPS)    : %d\n", int64(n*m)*1000/totalT)
	fmt.Printf("mean: %.f ns, median: %.f ns, max: %.f ns, min: %.f ns, p99: %.f\n", mean, median, max, min, p99)
	fmt.Printf("mean: %d ms, median: %d ms, max: %d ms, min: %d ms, 99.9%: %d\n",
		int64(mean/1000000), int64(median/1000000), int64(max/1000000), int64(min/1000000), int64(p99/1000000))

	closeClients(clients)
}

func prepareClients(host string, sharedClientNum int, serviceMethodName string, args *BenchmarkMessage) []*rpcx.Client {
	s := &rpcx.DirectClientSelector{Network: "tcp", Address: host}
	r := make([]*rpcx.Client, sharedClientNum, sharedClientNum)
	var reply BenchmarkMessage

	for i := 0; i < sharedClientNum; i++ {
		client := rpcx.NewClient(s)
		client.ClientCodecFunc = codec.NewProtobufClientCodec
		//warmup
		for j := 0; j < 5; j++ {
			client.Call(serviceMethodName, args, &reply)
		}

		r[i] = client
	}

	return r
}

func closeClients(clients []*rpcx.Client) {
	for _, client := range clients {
		client.Close()
	}
}

func prepareArgs() *BenchmarkMessage {
	b := true
	var i int32 = 100000
	var s = "许多往事在眼前一幕一幕，变的那麼模糊"

	var args BenchmarkMessage

	v := reflect.ValueOf(&args).Elem()
	num := v.NumField()
	for k := 0; k < num; k++ {
		field := v.Field(k)
		if field.Type().Kind() == reflect.Ptr {
			switch v.Field(k).Type().Elem().Kind() {
			case reflect.Int, reflect.Int32, reflect.Int64:
				field.Set(reflect.ValueOf(&i))
			case reflect.Bool:
				field.Set(reflect.ValueOf(&b))
			case reflect.String:
				field.Set(reflect.ValueOf(&s))
			}
		} else {
			switch field.Kind() {
			case reflect.Int, reflect.Int32, reflect.Int64:
				field.SetInt(100000)
			case reflect.Bool:
				field.SetBool(true)
			case reflect.String:
				field.SetString(s)
			}
		}

	}
	return &args
}
