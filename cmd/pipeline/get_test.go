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

	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/util"
)

func getRootCmdForTest() *cobra.Command {
	rootCmd := &cobra.Command{}
	rootCmd.PersistentFlags().String("config", "", "config file (default is $HOME/.spin/config)")
	rootCmd.PersistentFlags().String("gate-endpoint", "", "Gate (API server) endpoint. Default http://localhost:8084")
	rootCmd.PersistentFlags().Bool("insecure", false, "Ignore Certificate Errors")
	rootCmd.PersistentFlags().Bool("quiet", false, "Squelch non-essential output")
	rootCmd.PersistentFlags().Bool("no-color", false, "Disable color")
	rootCmd.PersistentFlags().String("output", "", "Configure output formatting")
	rootCmd.PersistentFlags().String("default-headers", "", "Configure addtional headers for gate client requests")
	util.InitUI(false, false, "")
	return rootCmd
}

func TestPipelineGet_basic(t *testing.T) {
	ts := testGatePipelineGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "get", "--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_flags(t *testing.T) {
	ts := testGatePipelineGetSuccess()
	defer ts.Close()

	args := []string{"pipeline", "get", "--gate-endpoint", ts.URL} // Missing application and name.
	currentCmd := NewGetCmd(pipelineOptions{})
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

func TestPipelineGet_malformed(t *testing.T) {
	ts := testGatePipelineGetMalformed()
	defer ts.Close()

	args := []string{"pipeline", "get", "--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	currentCmd := NewGetCmd(pipelineOptions{})
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

func TestPipelineGet_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	args := []string{"pipeline", "get", "--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	currentCmd := NewGetCmd(pipelineOptions{})
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

func TestPipelineGet_notfound(t *testing.T) {
	ts := testGatePipelineGetMissing()
	defer ts.Close()

	args := []string{"pipeline", "get", "--application", "app", "--name", "two", "--gate-endpoint", ts.URL}
	currentCmd := NewGetCmd(pipelineOptions{})
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

// testGatePipelineGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline get response.
func testGatePipelineGetSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(pipelineGetJson))
	}))
	return httptest.NewServer(mux)
}

// testGatePipelineGetMalformed returns a malformed get response of pipeline configs.
func testGatePipelineGetMalformed() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedPipelineGetJson))
	}))
	return httptest.NewServer(mux)
}

// testGatePipelineGetMissing returns a 404 Not Found for an errant pipeline name|application pair.
func testGatePipelineGetMissing() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
	}))
	return httptest.NewServer(mux)
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
