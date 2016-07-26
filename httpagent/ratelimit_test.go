package httpagent

import "testing"
import "github.com/juju/ratelimit"
import "time"

func TestRateLimit(t *testing.T) {
	bucket := ratelimit.NewBucketWithRate(1000, 1000)
	t.Logf("Rate: %f", bucket.Rate())

	for tries := 5; tries > 0; tries-- {
		for i := 0; i < 20; i++ {
			token := bucket.TakeAvailable(100)

			if i < 10 {
				if token != 100 {
					t.Errorf("Expected %d but %d", 100, token)
				}
			} else {
				if token != 0 {
					t.Errorf("Expected %d but %d", 0, token)
				}
			}
		}

		time.Sleep(1 * time.Second)
	}

}
