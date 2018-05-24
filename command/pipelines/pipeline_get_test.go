package pipelines

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/command"
)

func TestPipelineGet_basic(t *testing.T) {
	ts := testGatePipelineGetSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineGet_flags(t *testing.T) {
	ts := testGatePipelineGetSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL} // Missing application and name.
	cmd := PipelineGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, flags are malformed.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineGet_malformed(t *testing.T) {
	ts := testGatePipelineGetMalformed()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, return payload is malformed.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineGet_fail(t *testing.T) {
	ts := testGateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineGet_notfound(t *testing.T) {
	ts := testGatePipelineGetMissing()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--application", "app", "--name", "two", "--gate-endpoint", ts.URL}
	cmd := PipelineGetCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

// testGatePipelineGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline get response.
func testGatePipelineGetSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(pipelineGetJson))
	}))
}

// testGatePipelineGetMalformed returns a malformed get response of pipeline configs.
func testGatePipelineGetMalformed() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedPipelineGetJson))
	}))
}

// testGatePipelineGetMissing returns a 404 Not Found for an errant pipeline name|application pair.
func testGatePipelineGetMissing() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
	}))
}

const malformedPipelineGetJson = `
  "application": "app",
  "id": "pipeline_one",
  "index": 0,
  "keepWaitingPipelines": false,
  "lastModifiedBy": "jacobkiefer@google.com",
  "limitConcurrent": true,
  "name": "one",
  "parameterConfig": [
    {
      "default": "blah",
      "description": "A foo.",
      "name": "foooB",
      "required": true
    }
  ],
  "stages": [
    {
      "comments": "${ parameters.derp }",
      "name": "Wait",
      "refId": "1",
      "requisiteStageRefIds": [],
      "type": "wait",
      "waitTime": 30
    }
  ],
  "triggers": [],
  "updateTs": "1526578883109"
}
`

const pipelineGetJson = `
{
  "application": "app",
  "id": "pipeline_one",
  "index": 0,
  "keepWaitingPipelines": false,
  "lastModifiedBy": "jacobkiefer@google.com",
  "limitConcurrent": true,
  "name": "one",
  "parameterConfig": [
    {
      "default": "blah",
      "description": "A foo.",
      "name": "foooB",
      "required": true
    }
  ],
  "stages": [
    {
      "comments": "${ parameters.derp }",
      "name": "Wait",
      "refId": "1",
      "requisiteStageRefIds": [],
      "type": "wait",
      "waitTime": 30
    }
  ],
  "triggers": [],
  "updateTs": "1526578883109"
}
`
