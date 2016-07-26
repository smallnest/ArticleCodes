package httpagent

import (
	"encoding/binary"
	"net"
	"strings"
	"sync"
	"testing"
	"time"
)

var once sync.Once

func startTestServer() {
	go Start("", 0)
	time.Sleep(2 * time.Second)
}

func TestStart(t *testing.T) {
	once.Do(startTestServer)

	conn, err := net.Dial("tcp", "127.0.0.1:8899")
	if err != nil {
		t.Errorf("can't connect the server because of %v", err)
	}
	lockconn := &LockConn{Conn: conn}
	defer lockconn.Close()
	//t.Logf("connected: local address: %s, remote address: %s", conn.LocalAddr().String(), conn.RemoteAddr().String())

	//maxRedirectsCount = 10
	//req := Request{Id: 1000, Method: "GET", Url: "http://toutiao.io/j/5bwvs7"}
	req := Request{Id: 1000, Method: "GET", Url: "http://www.google.com/"}
	bytes, err := req.Marshal()
	if err != nil {
		t.Errorf("can't marshal Request because of %v", err)
	}

	err = lockconn.writeFrame(bytes)
	if err != nil {
		t.Errorf("can't write length of this frame because of %v", err)
	}

	resp := handleTestResponse(t, conn)

	if resp.IsReply && (resp.Id != 1000 || resp.StatusCode != 200) {
		t.Errorf("Not Expected Reply. Received: %v", err)
	}

	resp = handleTestResponse(t, conn)
	if !resp.IsReply && (resp.Id != 1000 || resp.StatusCode != 200) {
		t.Errorf("Not Expected Response. Received: %v, response: %+v", err, resp)
	}
}

func handleTestResponse(t *testing.T, conn net.Conn) (resp Response) {
	var l int32
	err := binary.Read(conn, binary.BigEndian, &l)
	if err != nil {
		t.Errorf("failed to read length from Reply  because of %v", err)
		return resp
	}

	msgBytes := make([]byte, l, l)
	_, err = conn.Read(msgBytes)
	if err != nil {
		t.Errorf("failed to read Reply because of %v", err)
		return resp
	}

	err = resp.Unmarshal(msgBytes)
	if err != nil {
		t.Errorf("failed to unmarshal Reply because of %v", err)
		return resp
	}

	return resp
}

func TestGet(t *testing.T) {
	once.Do(startTestServer)

	conn, err := net.Dial("tcp", "127.0.0.1:8899")
	if err != nil {
		t.Errorf("can't connect the server because of %v", err)
	}
	lockconn := &LockConn{Conn: conn}

	defer lockconn.Close()

	//t.Logf("connected: local address: %s, remote address: %s", conn.LocalAddr().String(), conn.RemoteAddr().String())

	req := Request{Id: 1000, Method: "GET", Url: "http://httpbin.org/get"}
	bytes, err := req.Marshal()
	if err != nil {
		t.Errorf("can't marshal Request because of %v", err)
	}

	err = lockconn.writeFrame(bytes)
	if err != nil {
		t.Errorf("can't write length of this frame because of %v", err)
	}

	resp := handleTestResponse(t, conn)

	if resp.IsReply && (resp.Id != 1000 || resp.StatusCode != 200) {
		t.Errorf("Not Expected Reply. Received: %v", err)
	}

	resp = handleTestResponse(t, conn)
	if !resp.IsReply && (resp.Id != 1000 || resp.StatusCode != 200) {
		t.Errorf("Not Expected Response. Received: %v, response: %+v", err, resp)
	}

	if !strings.Contains(b2s(resp.Body), userAgent) {
		t.Errorf("Not Expected Response. Received: %v, response: %+v", err, resp)
	}
}

func TestPost(t *testing.T) {
	once.Do(startTestServer)

	conn, err := net.Dial("tcp", "127.0.0.1:8899")
	if err != nil {
		t.Errorf("can't connect the server because of %v", err)
	}
	lockconn := &LockConn{Conn: conn}
	defer lockconn.Close()

	//t.Logf("connected: local address: %s, remote address: %s", conn.LocalAddr().String(), conn.RemoteAddr().String())

	headers := []*HeaderEntry{&HeaderEntry{Key: "ContentType", Value: "text/plain"}}
	req := Request{Id: 1000, Method: "POST", Url: "http://httpbin.org/post", Body: s2b("hello world"), Headers: headers}
	bytes, err := req.Marshal()
	if err != nil {
		t.Errorf("can't marshal Request because of %v", err)
	}

	err = lockconn.writeFrame(bytes)
	if err != nil {
		t.Errorf("can't write length of this frame because of %v", err)
	}

	resp := handleTestResponse(t, conn)

	if resp.IsReply && (resp.Id != 1000 || resp.StatusCode != 200) {
		t.Errorf("Not Expected Reply. Received: %v", err)
	}

	resp = handleTestResponse(t, conn)
	if !resp.IsReply && (resp.Id != 1000 || resp.StatusCode != 200) {
		t.Errorf("Not Expected Response. Received: %v, response: %+v", err, resp)
	}

	if !strings.Contains(b2s(resp.Body), "hello world") {
		t.Errorf("Not Expected Response. Received: %v, response: %+v", err, resp)
	}
}
