package httpagent

import (
	"errors"
	"time"

	log "github.com/Sirupsen/logrus"
	"github.com/valyala/fasthttp"
)

var (
	readTimeOut       = props.GetParsedDuration("readTimeout", 5*time.Second)
	writeTimeOut      = props.GetParsedDuration("writeTimeout", 5*time.Second)
	idleTimeOut       = props.GetParsedDuration("idleTimeout", 5*time.Minute)
	maxRedirectsCount = props.GetInt("maxRedirectsCount", 16)
	userAgent         = props.GetString("userAgent", "fasthttp")
	maxConnsPerHost   = props.GetInt("maxConnsPerHost", 512)
)

var (
	errMissingLocation  = errors.New("missing Location header for http redirect")
	errTooManyRedirects = errors.New("too many redirects detected when doing the request")
)

var client = fasthttp.Client{
	ReadTimeout:         readTimeOut,
	WriteTimeout:        writeTimeOut,
	MaxIdleConnDuration: idleTimeOut,
	DialDualStack:       true,
	MaxConnsPerHost:     maxConnsPerHost,
}

// handleRequest sends this request to remote http server
func handleRequest(lockconn *LockConn, req *Request) {

	statusCode, body, location, entries, err := sendByFastHttp(req)

	handleResponse(lockconn, req.Id, statusCode, body, location, entries, err)
}

func handleResponse(lockconn *LockConn, id int32, statusCode int, body []byte, location []byte, entries []*HeaderEntry, err error) {
	var resp Response

	if err != nil {
		resp = Response{
			Id:         id,
			StatusCode: int32(500),
			Body:       []byte(err.Error()),
		}
	} else {
		resp = Response{
			Id:         id,
			StatusCode: int32(statusCode),
			Body:       body,
		}

		if location != nil {
			resp.IsRedirect = true
			resp.Location = b2s(location)
		}
	}

	resp.IsReply = false
	resp.Headers = entries

	data, err := resp.Marshal()
	if err != nil {
		log.Warnf("failed to marshal Response because of %v", err)
		return
	}

	err = lockconn.writeFrame(data)
	if err != nil {
		log.Warnf("failed to write Response to vex because of %v", err)
	}
}

func sendByFastHttp(req *Request) (statusCode int, body []byte, location []byte, entries []*HeaderEntry, err error) {
	request := fasthttp.AcquireRequest()
	request.SetBody(req.Body)
	request.Header.SetMethod(req.Method)
	request.Header.SetContentLength(len(req.Body))
	request.Header.SetUserAgent(userAgent)
	copyMap2Header(req.Headers, &request.Header)

	if req.Body != nil {
		request.SetBody(req.Body)
	}

	resp := fasthttp.AcquireResponse()

	var url = req.Url

	redirectsCount := 0
	for {
		request.Header.SetHost("")
		request.SetRequestURI(url)
		if err = client.Do(request, resp); err != nil {
			break
		}

		statusCode = resp.Header.StatusCode()

		if statusCode != fasthttp.StatusMovedPermanently && statusCode != fasthttp.StatusFound && statusCode != fasthttp.StatusSeeOther {
			location = nil
			break
		}

		if maxRedirectsCount == 0 {
			location = resp.Header.Peek("Location")
			break
		}

		redirectsCount++
		if redirectsCount > maxRedirectsCount {
			err = errTooManyRedirects
			break
		}
		location = resp.Header.Peek("Location")

		if len(location) == 0 {
			err = errMissingLocation
			break
		}
		url = getRedirectURL(url, location)
	}
	fasthttp.ReleaseRequest(request)

	b := resp.Body()
	body = make([]byte, len(b), len(b))
	copy(body, b)

	entries = copyHeader2Map(&resp.Header, []*HeaderEntry{})
	fasthttp.ReleaseResponse(resp)

	return
}

func getRedirectURL(baseURL string, location []byte) string {
	u := fasthttp.AcquireURI()
	u.Update(baseURL)
	u.UpdateBytes(location)
	redirectURL := u.String()
	fasthttp.ReleaseURI(u)
	return redirectURL
}
