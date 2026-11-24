// Copyright (c) 2020, Anosua "Chini" Mukhopadhyay
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
	"bytes"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/spinnaker/spinnaker/spin/cmd"
	"github.com/spinnaker/spinnaker/spin/util"
)

func TestPipelineUpdate_carriesForwardUpdateTsAndStaleCheck(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	var saveQuery url.Values
	ts := testGateUpdatePipelineSuccess(saveBuffer, &saveQuery, "1772644108777")
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "update", "-a", "app", "-n", "pipeline1", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	if err := rootCmd.Execute(); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	if got := saveQuery.Get("staleCheck"); got != "true" {
		t.Fatalf("expected staleCheck=true on the save request by default, got %q", got)
	}
	if !strings.Contains(saveBuffer.String(), `"updateTs":"1772644108777"`) {
		t.Fatalf("expected the fetched updateTs to be carried into the save body, got: %s", saveBuffer.String())
	}
}

func TestPipelineUpdate_staleCheckDisabled(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	var saveQuery url.Values
	ts := testGateUpdatePipelineSuccess(saveBuffer, &saveQuery, "1772644108777")
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "update", "-a", "app", "-n", "pipeline1", "--gate-endpoint", ts.URL, "--stale-check=false"}
	rootCmd.SetArgs(args)
	if err := rootCmd.Execute(); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	if got := saveQuery.Get("staleCheck"); got != "" {
		t.Fatalf("expected no staleCheck param with --stale-check=false, got %q", got)
	}
}

func TestPipelineUpdate_staleRejection(t *testing.T) {
	ts := testGatePipelineSaveStale()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "update", "-a", "app", "-n", "pipeline1", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatal("expected a stale-pipeline error, got none")
	}
	if !strings.Contains(err.Error(), "was modified in Spinnaker") {
		t.Fatalf("expected a friendly stale-pipeline message, got: %s", err.Error())
	}
}

func TestPipelineUpdate_notFound(t *testing.T) {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/app/pipelineConfigs/pipeline1", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	ts := httptest.NewServer(mux)
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "update", "-a", "app", "-n", "pipeline1", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	if err := rootCmd.Execute(); err == nil {
		t.Fatal("expected a not-found error, got none")
	}
}

// testGateUpdatePipelineSuccess spins up a local http server whose pipeline lookup returns
// an existing pipeline carrying the given updateTs, and whose save endpoint records the
// request body and query string for assertions.
func testGateUpdatePipelineSuccess(buffer *bytes.Buffer, queryCapture *url.Values, updateTs string) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/applications/app/pipelineConfigs/pipeline1",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Add("content-type", "application/json")
			fmt.Fprintf(w, `{"id":"pipeline1","name":"pipeline1","application":"app","updateTs":"%s"}`, updateTs)
		}),
	)
	mux.Handle(
		"/pipelines",
		util.NewTestBufferHandlerFuncCapturingQuery(http.MethodPost, buffer, queryCapture, http.StatusOK, ""),
	)
	return httptest.NewServer(mux)
}
