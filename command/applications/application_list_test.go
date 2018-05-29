package applications

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/command"
)

func TestApplicationList_basic(t *testing.T) {
	ts := testGateApplicationListSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL}
	cmd := ApplicationListCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationList_malformed(t *testing.T) {
	ts := testGateApplicationListMalformed()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL}
	cmd := ApplicationListCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, return payload is malformed.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationList_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL}
	cmd := ApplicationListCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

// testGateApplicationListSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed application list.
func testGateApplicationListSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(applicationListJson))
	}))
}

// testGateApplicationListMalformed returns a malformed list of application configs.
func testGateApplicationListMalformed() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedApplicationListJson))
	}))
}

const malformedApplicationListJson = `
  {
    "accounts": "account1",
    "cloudproviders": [
      "gce",
      "kubernetes"
    ],
    "createTs": "1527261941734",
    "email": "app",
    "instancePort": 80,
    "lastModifiedBy": "anonymous",
    "name": "app",
    "updateTs": "1527261941735",
    "user": "anonymous",
  }
]
`

const applicationListJson = `
[
  {
    "accounts": "account1",
    "cloudproviders": [
      "gce",
      "kubernetes"
    ],
    "createTs": "1527261941734",
    "email": "app",
    "instancePort": 80,
    "lastModifiedBy": "anonymous",
    "name": "app",
    "updateTs": "1527261941735",
    "user": "anonymous"
  }
]
`
