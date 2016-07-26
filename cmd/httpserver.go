package main

import (
	"flag"
	"io"
	"net/http"
	"strconv"
	"time"
)

var testHost = flag.String("host", "", "host")
var testPort = flag.Int("port", 8999, "port")
var sleep = flag.Int("sleep", 0, "sleep time")

func main() {
	flag.Parse()

	sleepTime := *sleep

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if sleepTime > 0 {
			time.Sleep(time.Duration(sleepTime) * time.Millisecond)
		}
		io.Copy(w, r.Body)
	})

	http.ListenAndServe((*testHost)+":"+strconv.Itoa(*testPort), nil)
}
