// Copyright (c) 2019, Google, Inc.
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

package execution

import (
	"fmt"
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/util"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
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

func TestExecutionGet_basic(t *testing.T) {
	ts := testGateExecutionGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd()
	rootCmd := getRootCmdForTest()

	executionCmd := NewExecutionCmd(os.Stdout)
	executionCmd.AddCommand(currentCmd)

	rootCmd.AddCommand(executionCmd)

	// Exclude 'pipeline' since we are testing only the 'execution' subcommand.
	args := []string{"ex", "get", "someId", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestExecutionGet_noinput(t *testing.T) {
	ts := testGateExecutionGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd()
	rootCmd := getRootCmdForTest()

	executionCmd := NewExecutionCmd(os.Stdout)
	executionCmd.AddCommand(currentCmd)

	rootCmd.AddCommand(executionCmd)

	// Exclude 'pipeline' since we are testing only the 'execution' subcommand.
	args := []string{"ex", "get", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestExecutionGet_failure(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()
	currentCmd := NewGetCmd()
	rootCmd := getRootCmdForTest()

	executionCmd := NewExecutionCmd(os.Stdout)
	executionCmd.AddCommand(currentCmd)

	rootCmd.AddCommand(executionCmd)

	// Exclude 'pipeline' since we are testing only the 'execution' subcommand.
	args := []string{"ex", "get", "someId", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGateExecutionGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline get response.
func testGateExecutionGetSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(executionGetJson))
	}))
}

// GateServerFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateServerFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}

const executionGetJson = `
[
 {
  "application": "jtk54",
  "authentication": {
   "allowedAccounts": [
    "gce",
    "docker",
    "default"
   ],
   "user": "anonymous"
  },
  "canceled": false,
  "id": "someId",
  "initialConfig": {},
  "keepWaitingPipelines": false,
  "limitConcurrent": true,
  "name": "spin pipeline",
  "notifications": [],
  "origin": "api",
  "pipelineConfigId": "1",
  "stages": [
   {
    "context": {
     "comments": "I have no quarrel with ye.",
     "startTime": 1550686554744,
     "waitTime": 30
    },
    "id": "01D461XRATNBYMXDFZ24V27WGJ",
    "name": "Wait",
    "outputs": {},
    "refId": "1",
    "requisiteStageRefIds": [],
    "startTime": 1550686554297,
    "status": "RUNNING",
    "tasks": [
     {
      "id": "1",
      "implementingClass": "com.netflix.spinnaker.orca.pipeline.tasks.WaitTask",
      "loopEnd": false,
      "loopStart": false,
      "name": "wait",
      "stageEnd": true,
      "stageStart": true,
      "startTime": 1550686554503,
      "status": "RUNNING"
     }
    ],
    "type": "wait"
   }
  ],
  "startTime": 1550686554054,
  "status": "RUNNING",
  "systemNotifications": [],
  "trigger": {
   "artifacts": [],
   "dryRun": false,
   "notifications": [],
   "parameters": {},
   "rebake": false,
   "resolvedExpectedArtifacts": [],
   "strategy": false,
   "type": "manual",
   "user": "anonymous"
  },
  "type": "PIPELINE"
 }
]
`
