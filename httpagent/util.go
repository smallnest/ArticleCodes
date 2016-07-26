package httpagent

import (
	"reflect"
	"unsafe"

	"github.com/valyala/fasthttp"
)

func b2s(b []byte) string {
	return *(*string)(unsafe.Pointer(&b))
}

func s2b(s string) []byte {
	sh := (*reflect.StringHeader)(unsafe.Pointer(&s))
	bh := reflect.SliceHeader{
		Data: sh.Data,
		Len:  sh.Len,
		Cap:  sh.Len,
	}
	return *(*[]byte)(unsafe.Pointer(&bh))
}

func copyMap2Header(entries []*HeaderEntry, header *fasthttp.RequestHeader) {
	for _, e := range entries {
		header.Set(e.Key, e.Value)
	}
}

func copyHeader2Map(header *fasthttp.ResponseHeader, entries []*HeaderEntry) []*HeaderEntry {
	header.VisitAll(func(key []byte, value []byte) {
		entries = append(entries, &HeaderEntry{Key: b2s(key), Value: b2s(value)})
	})

	return entries
}
