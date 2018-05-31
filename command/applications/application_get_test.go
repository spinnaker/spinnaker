package applications

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/command"
)

const (
	APP = "app"
)

func TestApplicationGet_basic(t *testing.T) {
	ts := testGateApplicationGetSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL, APP}
	cmd := ApplicationGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationGet_flags(t *testing.T) {
	ts := testGateApplicationGetSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL} // Missing positional arg.
	cmd := ApplicationGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, flags are malformed.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationGet_malformed(t *testing.T) {
	ts := testGateApplicationGetMalformed()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL, APP}
	cmd := ApplicationGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, return payload is malformed.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationGet_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL, APP}
	cmd := ApplicationGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

// testGateApplicationGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline list.
func testGateApplicationGetSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(applicationJson))
	}))
}

// testGateApplicationGetMalformed returns a malformed list of pipeline configs.
func testGateApplicationGetMalformed() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedApplicationGetJson))
	}))
}

const malformedApplicationGetJson = `
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
`

const applicationJson = `
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
`
