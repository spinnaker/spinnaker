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
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/pipeline"
)

func TestExecutionCancel_basic(t *testing.T) {
	ts := testGateExecutionCancelSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, pipelineOpts := pipeline.NewPipelineCmd(rootOpts)
	pipelineCmd.AddCommand(NewExecutionCmd(pipelineOpts))
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "ex", "cancel", "someId", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestExecutionCancel_noinput(t *testing.T) {
	ts := testGateExecutionCancelSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, pipelineOpts := pipeline.NewPipelineCmd(rootOpts)
	pipelineCmd.AddCommand(NewExecutionCmd(pipelineOpts))
	rootCmd.AddCommand(pipelineCmd)

	// Exclude 'pipeline' since we are testing only the 'execution' subcommand.
	args := []string{"pipeline", "ex", "cancel", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %v", err)
	}
}

func TestExecutionCancel_failure(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, pipelineOpts := pipeline.NewPipelineCmd(rootOpts)
	pipelineCmd.AddCommand(NewExecutionCmd(pipelineOpts))
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "ex", "cancel", "someId", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %v", err)
	}
}

// testGateExecutionCancelSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline get response.
func testGateExecutionCancelSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.String(), "/version") {
			payload := map[string]string{
				"version": "Unknown",
			}
			b, _ := json.Marshal(&payload)
			fmt.Fprintln(w, string(b))
		} else {
			fmt.Fprintln(w, "")
		}
	}))
}
