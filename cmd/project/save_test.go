// Copyright (c) 2020, Anosua Chini Mukhopadhyay
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

package project

import (
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

const (
	NAME              = "prj"
	EMAIL             = "user@spinnaker.com"
	PROJECT_TEST_FILE = "../../util/json_test_files/example_project.json"
)

func TestProjectSave_basic(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateProjectSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewProjectCmd(options))

	args := []string{
		"project", "save",
		"--gate-endpoint=" + ts.URL,
		"--name", NAME,
		"--email", EMAIL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testProjectTaskJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestProjectSave_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewProjectCmd(options))

	args := []string{
		"project", "save",
		"--gate-endpoint=" + ts.URL,
		"--name", NAME,
		"--email", EMAIL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestProjectSave_flags(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateProjectSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewProjectCmd(options))

	args := []string{
		"project", "save",
		"--gate-endpoint=" + ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := ""
	recieved := strings.TrimSpace(saveBuffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected save request body:\n%s", recieved)
	}
}

func TestProjectSave_missingname(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateProjectSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewProjectCmd(options))

	args := []string{
		"project", "save",
		"--email", EMAIL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := ""
	recieved := strings.TrimSpace(saveBuffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected save request body:\n%s", recieved)
	}
}

func TestProjectSave_missingemail(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateProjectSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewProjectCmd(options))

	args := []string{
		"project", "save",
		"--name", NAME,
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := ""
	recieved := strings.TrimSpace(saveBuffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected save request body:\n%s", recieved)
	}
}

func TestProjectSave_filejson(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateProjectSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewProjectCmd(options))

	args := []string{
		"project", "save",
		"--file", PROJECT_TEST_FILE,
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testExpandedProjectTaskJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

// testGateFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func testGateFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}

// testGateProjectSaveSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with successful responses to pipeline execute API calls.
// Writes request body to buffer for testing.
func testGateProjectSaveSuccess(buffer io.Writer) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/tasks",
		util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusOK, strings.TrimSpace(testAppTaskRefJsonStr)),
	)
	mux.Handle("/tasks/id", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, strings.TrimSpace(testProjectTaskStatusJsonStr))
	}))
	return httptest.NewServer(mux)
}

const testAppTaskRefJsonStr = `
{
 "ref": "/tasks/id"
}
`

const testProjectTaskStatusJsonStr = `
{
 "status": "SUCCEEDED"
}
`

const testExpandedProjectTaskJsonStr = `
{
 "application": "spinnaker",
 "description": "Create Project: example project",
 "job": [
  {
   "project": {
    "config": {
     "applications": [],
     "clusters": [],
     "pipelineConfigs": []
    },
    "email": "user@spinnaker.com",
    "name": "example project",
    "user": "user@spinnaker.com"
   },
   "type": "upsertProject",
   "user": "user@spinnaker.com"
  }
 ],
 "project": "example project"
}
`

const testProjectTaskJsonStr = `
{
 "application": "spinnaker",
 "description": "Create Project: prj",
 "job": [
  {
   "project": {
    "email": "user@spinnaker.com",
    "name": "prj"
   },
   "type": "upsertProject",
   "user": "user@spinnaker.com"
  }
 ],
 "project": "prj"
}
`
