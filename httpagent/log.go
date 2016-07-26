package httpagent

import (
	log "github.com/Sirupsen/logrus"
)

func init() {
	log.SetFormatter(&log.TextFormatter{})
	log.SetLevel(log.DebugLevel)
	//log.AddHook()
}
