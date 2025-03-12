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
	"bytes"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/andreyvit/diff"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

func TestPipelineGet_json(t *testing.T) {
	ts := testGatePipelineGetSuccess()
	defer ts.Close()

	buffer := new(bytes.Buffer)
	rootCmd, rootOpts := cmd.NewCmdRoot(buffer, buffer)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "get", "--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(pipelineGetJson)
	recieved := strings.TrimSpace(buffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected command output:\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestPipelineGet_yaml(t *testing.T) {
	ts := testGatePipelineGetSuccess()
	defer ts.Close()

	buffer := new(bytes.Buffer)
	rootCmd, rootOpts := cmd.NewCmdRoot(buffer, buffer)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "get", "--application", "app", "--name", "one", "--output", "yaml", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(pipelineGetYaml)
	recieved := strings.TrimSpace(buffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected command output:\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestPipelineGet_flags(t *testing.T) {
	ts := testGatePipelineGetSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "get", "--gate-endpoint", ts.URL} // Missing application and name.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "get", "--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_notfound(t *testing.T) {
	ts := testGatePipelineGetMissing()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "get", "--application", "app", "--name", "two", "--gate-endpoint", ts.URL}
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
	mux.Handle("/applications/app/pipelineConfigs/one", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, strings.TrimSpace(pipelineGetJson))
	}))
	return httptest.NewServer(mux)
}

// testGatePipelineGetMissing returns a 404 Not Found for an errant pipeline name|application pair.
func testGatePipelineGetMissing() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/app/pipelineConfigs/two", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
	}))
	return httptest.NewServer(mux)
}

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

const pipelineGetYaml = `
application: app
id: pipeline_one
index: 0
keepWaitingPipelines: false
lastModifiedBy: jacobkiefer@google.com
limitConcurrent: true
name: one
parameterConfig:
- default: blah
  description: A foo.
  name: foooB
  required: true
stages:
- comments: ${ parameters.derp }
  name: Wait
  refId: "1"
  requisiteStageRefIds: []
  type: wait
  waitTime: 30
triggers: []
updateTs: "1526578883109"
`
