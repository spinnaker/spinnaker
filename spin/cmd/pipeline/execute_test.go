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
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/spinnaker/spinnaker/spin/cmd"
	"github.com/spinnaker/spinnaker/spin/util"
)

func TestPipelineExecute_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "execute", "--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineExecute_flags(t *testing.T) {
	ts := testGateSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "execute", "--gate-endpoint", ts.URL} // Missing pipeline app and name.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineExecute_missingname(t *testing.T) {
	ts := testGateSuccess()
	defer ts.Close()

	tempFile := tempPipelineFile(missingNameJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "execute", "--application", "app", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineExecute_missingapp(t *testing.T) {
	ts := testGateSuccess()
	defer ts.Close()

	tempFile := tempPipelineFile(missingAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "execute", "--name", "one", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// gateExecuteServer is a fake gate that accepts pipeline triggers on the v2 endpoint and makes the
// resulting execution visible after a configurable number of lookups, like an asynchronous echo would.
type gateExecuteServer struct {
	*httptest.Server
	notFoundLookups int // lookups of the execution that 404 before it exists
	lookups         int
	triggerBody     map[string]interface{}
	v1Called        bool
}

func newGateExecuteServer(notFoundLookups int, ref string) *gateExecuteServer {
	g := &gateExecuteServer{notFoundLookups: notFoundLookups}
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/pipelines/v2/app/one", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewDecoder(r.Body).Decode(&g.triggerBody)
		w.Header().Add("content-type", "application/json")
		w.WriteHeader(http.StatusAccepted)
		fmt.Fprintf(w, `{"eventId": "e1", "ref": %q}`, ref)
	}))
	mux.Handle("/pipelines/app/one", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		g.v1Called = true
		w.WriteHeader(http.StatusAccepted)
	}))
	mux.Handle("/pipelines/01EXEC", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		g.lookups++
		if g.lookups <= g.notFoundLookups {
			http.Error(w, `{"message": "Pipeline not found"}`, http.StatusNotFound)
			return
		}
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, `{"id": "01EXEC", "status": "RUNNING"}`)
	}))
	g.Server = httptest.NewServer(mux)
	return g
}

func executePipelineAgainst(t *testing.T, ts *gateExecuteServer, extraArgs ...string) error {
	t.Helper()
	startPollInterval = time.Millisecond
	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := append([]string{"pipeline", "execute", "--application", "app", "--name", "one", "--gate-endpoint", ts.URL}, extraArgs...)
	rootCmd.SetArgs(args)
	return rootCmd.Execute()
}

func TestPipelineExecute_waitsForExecutionToStart(t *testing.T) {
	ts := newGateExecuteServer(3, "/pipelines/01EXEC")
	defer ts.Close()

	if err := executePipelineAgainst(t, ts, "-p", "k=v"); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
	if ts.lookups != 4 {
		t.Errorf("expected to poll until the execution existed (4 lookups), got %d", ts.lookups)
	}
	if ts.v1Called {
		t.Error("the deprecated v1 trigger endpoint should not be used")
	}
	if ts.triggerBody["type"] != "manual" {
		t.Errorf("expected a flat manual trigger, got %v", ts.triggerBody)
	}
	if params, _ := ts.triggerBody["parameters"].(map[string]interface{}); params["k"] != "v" {
		t.Errorf("expected parameter k=v in trigger, got %v", ts.triggerBody)
	}
}

func TestPipelineExecute_startTimeout(t *testing.T) {
	ts := newGateExecuteServer(1000000, "/pipelines/01EXEC")
	defer ts.Close()

	err := executePipelineAgainst(t, ts, "--start-timeout", "50ms")
	if err == nil || !strings.Contains(err.Error(), "had not started") {
		t.Fatalf("expected a start timeout error, got: %v", err)
	}
}

func TestPipelineExecute_missingRef(t *testing.T) {
	ts := newGateExecuteServer(0, "")
	defer ts.Close()

	err := executePipelineAgainst(t, ts)
	if err == nil || !strings.Contains(err.Error(), "did not return an execution reference") {
		t.Fatalf("expected a missing reference error, got: %v", err)
	}
}
