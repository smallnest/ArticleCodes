package httpagent

import (
	"encoding/binary"
	"io"
	"io/ioutil"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
)

func BenchmarkHttpAgent(b *testing.B) {
	once.Do(startTestServer)

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.Copy(w, r.Body)
	}))
	defer ts.Close()

	req := Request{Id: 1000, Method: "GET", Url: ts.URL}
	bytes, err := req.Marshal()
	if err != nil {
		b.Errorf("can't marshal Request because of %v", err)
	}

	conn, err := net.Dial("tcp", "127.0.0.1:8899")
	if err != nil {
		b.Errorf("can't connect the server because of %v", err)
	}
	lockconn := &LockConn{Conn: conn}

	num := 0
	b.ReportAllocs()
	b.ResetTimer()
	for n := 0; n < b.N; n++ {
		num++
		err = lockconn.writeFrame(bytes)
		for err != nil {
			b.Logf("can't write length of this frame because of %v", err)
			conn.Close()
			conn, err = net.Dial("tcp", "127.0.0.1:8899")
			lockconn = &LockConn{Conn: conn}
			num = 1
		}
		resp := handleBenchmarkTestResponse(b, conn)
		if resp.StatusCode == 200 {
			handleBenchmarkTestResponse(b, conn)
		}
	}

	conn.Close()
}

func BenchmarkGoDirectHttp(b *testing.B) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.Copy(w, r.Body)
	}))
	defer ts.Close()
	url := ts.URL

	for n := 0; n < b.N; n++ {
		if resp, err := http.Get(url); err == nil {
			_, _ = ioutil.ReadAll(resp.Body)
			resp.Body.Close()
		}
	}
}

func handleBenchmarkTestResponse(b *testing.B, conn net.Conn) (resp Response) {
	var l int32
	err := binary.Read(conn, binary.BigEndian, &l)
	if err != nil {
		b.Errorf("failed to read length from Reply  because of %v", err)
		return resp
	}

	msgBytes := make([]byte, l, l)
	_, err = conn.Read(msgBytes)
	if err != nil {
		b.Errorf("failed to read Reply because of %v", err)
		return resp
	}

	err = resp.Unmarshal(msgBytes)
	if err != nil {
		b.Errorf("failed to unmarshal Reply because of %v", err)
		return resp
	}

	return resp
}
