// Copyright (c) 2018, Google, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package pipeline

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/spinnaker/spin/util"
)

func TestPipelineList_basic(t *testing.T) {
	ts := testGatePipelineListSuccess()
	defer ts.Close()

	args := []string{"pipeline", "list", "--application", "app", "--gate-endpoint", ts.URL}
	currentCmd := NewListCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

}

func TestPipelineList_flags(t *testing.T) {
	ts := testGatePipelineListSuccess()
	defer ts.Close()

	args := []string{"pipeline", "list", "--gate-endpoint", ts.URL} // Missing application.
	currentCmd := NewListCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineList_malformed(t *testing.T) {
	ts := testGatePipelineListMalformed()
	defer ts.Close()

	args := []string{"pipeline", "list", "--application", "app", "--gate-endpoint", ts.URL}
	currentCmd := NewListCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineList_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	args := []string{"pipeline", "list", "--application", "app", "--gate-endpoint", ts.URL}
	currentCmd := NewListCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGatePipelineListSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline list.
func testGatePipelineListSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(pipelineListJson))
	}))
	return httptest.NewServer(mux)
}

// testGatePipelineListMalformed returns a malformed list of pipeline configs.
func testGatePipelineListMalformed() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedPipelineListJson))
	}))
	return httptest.NewServer(mux)
}

const malformedPipelineListJson = `
  {
    "application": "app",
    "id": "pipeline1",
    "index": 0,
    "keepWaitingPipelines": false,
    "lastModifiedBy": "jacobkiefer@google.com",
    "limitConcurrent": true,
    "name": "derp1",
    "parameterConfig": [
      {
        "default": "bar",
        "description": "A foo.",
        "name": "foo",
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
]
`

const pipelineListJson = `
[
  {
    "application": "app",
    "id": "pipeline1",
    "index": 0,
    "keepWaitingPipelines": false,
    "lastModifiedBy": "jacobkiefer@google.com",
    "limitConcurrent": true,
    "name": "derp1",
    "parameterConfig": [
      {
        "default": "bar",
        "description": "A foo.",
        "name": "foo",
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
]
`
