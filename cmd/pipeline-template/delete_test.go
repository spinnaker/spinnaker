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

package pipeline_template

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spinnaker/spin/cmd"
	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

func TestPipelineTemplateDelete_basic(t *testing.T) {
	ts := testGateDeleteSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "delete", "myTemplate", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_tag(t *testing.T) {
	ts := testGateDeleteSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "delete", "myTemplate", "--tag", "stable", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "delete", "myTemplate", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_flags(t *testing.T) {
	ts := testGateDeleteSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "delete", "--id", "myTemplate", "--gate-endpoint", ts.URL} // Extra --id flag.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_missingid(t *testing.T) {
	ts := testGateDeleteSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "delete", "--gate-endpoint", ts.URL} // Missing pipeline id.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command errantly succeeded. %s", err)
	}
}

// testGateDeleteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with OK to indicate a pipeline template exists,
// and Accepts POST calls.
func testGateDeleteSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/pipelineTemplates/myTemplate", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodDelete {
			resp := gate.ResponseEntity{StatusCode: "201 Accepted", StatusCodeValue: 201}
			b, _ := json.Marshal(&resp)

			w.WriteHeader(http.StatusAccepted)
			fmt.Fprintln(w, string(b)) // Write empty 201.
		} else {
			w.WriteHeader(http.StatusOK)
		}
	}))
	return httptest.NewServer(mux)
}
