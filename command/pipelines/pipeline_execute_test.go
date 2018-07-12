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

package pipelines

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/spinnaker/spin/command"
	gate "github.com/spinnaker/spin/gateapi"
)

// TODO(jacobkiefer): This test overlaps heavily with pipeline_save_test.go,
// consider factoring common testing code out.
func TestPipelineExecute_basic(t *testing.T) {
	ts := testGatePipelineExecuteSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineExecute_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineExecute_flags(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL} // Missing pipeline app and name.
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, flags are malformed.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineExecute_missingname(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(missingNameJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--application", "app", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, name is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineExecute_missingapp(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(missingAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, app is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

// testGatePipelineExecuteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with successful responses to pipeline execute API calls.
func testGatePipelineExecuteSuccess() *httptest.Server {
	mux := http.NewServeMux()
	mux.Handle("/pipelines/app/one", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		resp := gate.ResponseEntity{StatusCode: "201 Accepted", StatusCodeValue: 201}
		b, _ := json.Marshal(&resp)

		w.WriteHeader(http.StatusAccepted)
		fmt.Fprintln(w, string(b)) // Write empty 201.
	}))
	mux.Handle("/applications/app/executions/search", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		fmt.Fprintln(w, strings.TrimSpace(executions))
	}))
	return httptest.NewServer(mux)
}

const executions = `
[
  {
    "id": "asdflkj"
  }
]
`
