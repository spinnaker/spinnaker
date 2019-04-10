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
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	gate "github.com/spinnaker/spin/gateapi"
)

func TestPipelineTemplateDelete_basic(t *testing.T) {
	ts := gateServerDeleteSuccess()
	defer ts.Close()

	args := []string{"pipeline-template", "delete", "myTemplate", "--gate-endpoint", ts.URL}
	currentCmd := NewDeleteCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_tag(t *testing.T) {
	ts := gateServerDeleteSuccess()
	defer ts.Close()

	args := []string{"pipeline-template", "delete", "myTemplate", "--tag", "stable", "--gate-endpoint", ts.URL}
	currentCmd := NewDeleteCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	args := []string{"pipeline-template", "delete", "myTemplate", "--gate-endpoint", ts.URL}
	currentCmd := NewDeleteCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_flags(t *testing.T) {
	ts := gateServerDeleteSuccess()
	defer ts.Close()

	args := []string{"pipeline-template", "delete", "--id", "myTemplate", "--gate-endpoint", ts.URL} // Extra --id flag.
	currentCmd := NewDeleteCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateDelete_missingid(t *testing.T) {
	ts := gateServerDeleteSuccess()
	defer ts.Close()

	args := []string{"pipeline-template", "delete", "--gate-endpoint", ts.URL} // Missing pipeline id.
	currentCmd := NewDeleteCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command errantly succeeded. %s", err)
	}
}

// gateServerDeleteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with OK to indicate a pipeline template exists,
// and Accepts POST calls.
func gateServerDeleteSuccess() *httptest.Server {
	mux := http.NewServeMux()
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
