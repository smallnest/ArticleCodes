package httpagent

//go:generate protoc --gogoslick_out=. msg.proto
import (
	"encoding/binary"
	"io"
	"net"
	"strconv"
	"sync"

	log "github.com/Sirupsen/logrus"
	"github.com/facebookgo/grace/gracenet"
	"github.com/juju/ratelimit"
	"github.com/magiconair/properties"
)

var props = properties.MustLoadFile("httpagent.properties", properties.UTF8)
var (
	needReply            = props.GetBool("needReply", true)
	maxRequestsPerSecond = props.GetInt("maxRequestsPerSecond", 0)
)

var ln net.Listener

var bucket *ratelimit.Bucket

func init() {
	if maxRequestsPerSecond > 0 {
		bucket = ratelimit.NewBucketWithRate(float64(maxRequestsPerSecond), int64(maxRequestsPerSecond))
	}

}

type LockConn struct {
	net.Conn
	sync.Mutex
}

// Start HTTP agent and listen to accept connections from vex.
// It will parse requests and handle them.
// Requests will be redirect to appropriate HTTP Servers.
func Start(host string, port int) (err error) {
	if host == "" {
		host = props.GetString("host", "")
	}
	if port == 0 {
		port = props.GetInt("port", 8899)
	}

	var net gracenet.Net
	ln, err = net.Listen("tcp", host+":"+strconv.Itoa(port))

	if err != nil {
		log.Infof("can't start this server because of %v", err)
		return
	}

	log.Infof("Server started at %s", ln.Addr().String())

	for {
		conn, err := ln.Accept()
		if err == nil {
			go HandleConn(conn)
		}
	}
}

// Close this server
func Close() error {
	err := ln.Close()
	if err != nil {
		log.Infof("Server closed with error %v", err)
		return err
	}
	log.Infof("Server closed")
	return nil
}

// HandleConn reads requests and handle request in goroutines.
// It reads first 4 bytes as length of this frame and then read last bytes as protobuf stream.
// If you use netty, you can use LengthFieldPrepender(4) as the encoder.
func HandleConn(conn net.Conn) {
	defer func() {
		if r := recover(); r != nil {
			log.Infof("failed to handle conns because of %v", r)
			conn.Close()
		}
	}()

	lockconn := &LockConn{Conn: conn}

	//r := bufio.NewReader(conn)
	//w := bufio.NewWriter(conn)
	var msgLength int32

	for {
		err := binary.Read(conn, binary.BigEndian, &msgLength)
		if err != nil {
			if err == io.EOF {
				log.Infof("client is closed. conn: {%s, %s}", conn.LocalAddr().String(), conn.RemoteAddr().String())
			} else {
				log.Warnf("failed to read msgLength from clients because of %v, conn: {%s, %s}", err, conn.LocalAddr().String(), conn.RemoteAddr().String())
			}
			conn.Close()
			return
		}

		msgBytes := make([]byte, msgLength, msgLength)
		n, err := conn.Read(msgBytes)
		if n != int(msgLength) {
			log.Warnf("failed to read requests because the message length is wrong, received: %d, expected: %d", n, msgLength)
			conn.Close()
			return
		}
		if err != nil {
			if err == io.EOF {
				log.Infof("client is closed. conn: {%s, %s}", conn.LocalAddr().String(), conn.RemoteAddr().String())
			} else {
				log.Warnf("failed to read requests from clients because of %v, conn: {%s, %s}", err, conn.LocalAddr().String(), conn.RemoteAddr().String())
			}

			conn.Close()
			return
		}

		var req Request
		err = req.Unmarshal(msgBytes)
		if err != nil {
			log.Warnf("failed to unmarshal Request because of %v", err)
			conn.Close()
			return
		}

		reply := Response{Id: req.Id, StatusCode: 200, IsReply: true}

		availableToken := maxRequestsPerSecond <= 0 || bucket.TakeAvailable(1) == 1
		if !availableToken {
			reply.StatusCode = 503
		}

		msgBytes, err = reply.Marshal()
		if err != nil {
			log.Warnf("failed to marshal Reply because of %v", err)
			return
		}

		err = lockconn.writeFrame(msgBytes)
		if err != nil {
			log.Warnf("failed to send Reply because of %v", err)
			conn.Close()
			return
		}

		if availableToken {
			go handleRequest(lockconn, &req)
		}

	}

}

func (lc *LockConn) writeFrame(data []byte) error {
	msgLength := int32(len(data))
	lc.Lock()
	defer lc.Unlock()
	binary.Write(lc, binary.BigEndian, &msgLength)
	_, err := lc.Write(data)

	return err
}
