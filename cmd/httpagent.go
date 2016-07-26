package main

import (
	"flag"

	"bitbucket.org/thistech/vex/httpagent"
)

var (
	host = flag.String("host", "", "Server IP Address")
	port = flag.Int("port", 8899, "Server Port for accepting connnections from VEX")
)

func main() {
	flag.Parse()
	httpagent.Start(*host, *port)
}
