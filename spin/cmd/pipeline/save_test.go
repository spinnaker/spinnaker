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
	"io"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"

	"github.com/spinnaker/spinnaker/spin/cmd"
	"github.com/spinnaker/spinnaker/spin/util"
)

func TestPipelineSave_json(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	// updateTs is server-owned and stripped from the input file before POSTing; there is
	// no existing pipeline here for it to be carried forward from, so it's simply absent.
	expected := strings.TrimSpace(testPipelineJsonStrSaved)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineSave_yaml(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineYamlStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineJsonStrSaved)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineSave_stdin(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	// Prepare Stdin for test reading.
	tempFile.Seek(0, 0)
	oldStdin := os.Stdin
	defer func() { os.Stdin = oldStdin }()
	os.Stdin = tempFile

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineJsonStrSaved)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineSave_fail(t *testing.T) {
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

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_accessdenied(t *testing.T) {
	ts := testGateReadOnly()
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_flags(t *testing.T) {
	ts := testGateSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--gate-endpoint", ts.URL} // Missing pipeline spec file.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_missingname(t *testing.T) {
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

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_missingid(t *testing.T) {
	ts := testGateSuccess()
	defer ts.Close()

	tempFile := tempPipelineFile(missingIdJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_missingapp(t *testing.T) {
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

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_staleCheckOnByDefault(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	var saveQuery url.Values
	ts := testGatePipelineSaveSuccessCapturingQuery(saveBuffer, &saveQuery)
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	if err := rootCmd.Execute(); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	if got := saveQuery.Get("staleCheck"); got != "true" {
		t.Fatalf("expected staleCheck=true on the save request by default, got %q", got)
	}
}

func TestPipelineSave_staleCheckDisabled(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	var saveQuery url.Values
	ts := testGatePipelineSaveSuccessCapturingQuery(saveBuffer, &saveQuery)
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL, "--stale-check=false"}
	rootCmd.SetArgs(args)
	if err := rootCmd.Execute(); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	if got := saveQuery.Get("staleCheck"); got != "" {
		t.Fatalf("expected no staleCheck param with --stale-check=false, got %q", got)
	}
}

func TestPipelineSave_carriesForwardUpdateTs(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineSaveSuccessWithExistingUpdateTs(saveBuffer, "1772644108777")
	defer ts.Close()

	// The input file's own updateTs must never be trusted; only the value spin just
	// fetched from the server should make it onto the wire.
	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	if err := rootCmd.Execute(); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	if !strings.Contains(saveBuffer.String(), `"updateTs":"1772644108777"`) {
		t.Fatalf("expected the fetched updateTs to be carried into the save body, got: %s", saveBuffer.String())
	}
}

func TestPipelineSave_staleRejection(t *testing.T) {
	ts := testGatePipelineSaveStale()
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, _ := NewPipelineCmd(rootOpts)
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatal("expected a stale-pipeline error, got none")
	}
	if !strings.Contains(err.Error(), "was modified in Spinnaker") {
		t.Fatalf("expected a friendly stale-pipeline message, got: %s", err.Error())
	}
}

func tempPipelineFile(pipelineContent string) *os.File {
	tempFile, _ := ioutil.TempFile("" /* /tmp dir. */, "pipeline-spec")
	bytes, err := tempFile.Write([]byte(pipelineContent))
	if err != nil || bytes == 0 {
		fmt.Println("Could not write temp file.")
		return nil
	}
	return tempFile
}

// testGatePipelineSaveSuccess spins up a local http server that we will configure the
// GateClient to direct requests to. Responds with a 200 OK.
// Writes pipeline body to buffer for testing.
func testGatePipelineSaveSuccess(buffer io.Writer) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/applications/app/pipelineConfigs/pipeline1",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			// Confirm the pipeline exists.
			fmt.Fprintln(w, "")
		}),
	)
	mux.Handle(
		"/pipelines",
		util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusOK, ""),
	)
	return httptest.NewServer(mux)
}

// testGatePipelineSaveSuccessCapturingQuery behaves like testGatePipelineSaveSuccess, but
// also records the save request's query string into queryCapture -- for asserting on the
// staleCheck query param.
func testGatePipelineSaveSuccessCapturingQuery(buffer io.Writer, queryCapture *url.Values) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/applications/app/pipelineConfigs/pipeline1",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			// Confirm the pipeline exists.
			fmt.Fprintln(w, "")
		}),
	)
	mux.Handle(
		"/pipelines",
		util.NewTestBufferHandlerFuncCapturingQuery(http.MethodPost, buffer, queryCapture, http.StatusOK, ""),
	)
	return httptest.NewServer(mux)
}

// testGatePipelineSaveSuccessWithExistingUpdateTs behaves like testGatePipelineSaveSuccess,
// but the pipeline lookup returns an existing pipeline carrying the given updateTs.
func testGatePipelineSaveSuccessWithExistingUpdateTs(buffer io.Writer, updateTs string) *httptest.Server {
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
		util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusOK, ""),
	)
	return httptest.NewServer(mux)
}

// testGatePipelineSaveStale spins up a local http server whose pipeline lookup succeeds but
// whose save endpoint rejects the write the way front50 does when staleCheck fails.
func testGatePipelineSaveStale() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/applications/app/pipelineConfigs/pipeline1",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Add("content-type", "application/json")
			fmt.Fprintln(w, `{"id":"pipeline1","name":"pipeline1","application":"app","updateTs":"1"}`)
		}),
	)
	mux.Handle(
		"/pipelines",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, `{"message":"The submitted pipeline is stale.  submitted updateTs 1 does not match stored updateTs 2"}`, http.StatusBadRequest)
		}),
	)
	return httptest.NewServer(mux)
}

// testGateSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 OK.
func testGateSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, "") // Just write an empty 200 success on save.
	}))
	return httptest.NewServer(mux)
}

// testGateReadOnly spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 OK for READ-type requests (GET), 400 otherwise.
func testGateReadOnly() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch method := r.Method; method {
		case "GET":
			fmt.Fprintln(w, "") // Just write an empty 200 success on GET.
		default:
			// Return "400 Access is denied" (Bad Request)
			http.Error(w, "Access is denied", http.StatusBadRequest)
		}
	}))
	return httptest.NewServer(mux)
}

// testGateFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func testGateFail() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
	return httptest.NewServer(mux)
}

const missingNameJsonStr = `
{
  "id": "pipeline1",
  "application": "app",
  "keepWaitingPipelines": false,
  "lastModifiedBy": "anonymous",
  "limitConcurrent": true,
  "stages": [
    {
      "name": "Wait",
      "refId": "1",
      "requisiteStageRefIds": [],
      "type": "wait",
      "waitTime": 30
    }
  ],
  "triggers": [],
  "updateTs": "1520879791608"
}
`

const missingIdJsonStr = `
{
  "name": "pipeline1",
  "application": "app",
  "keepWaitingPipelines": false,
  "lastModifiedBy": "anonymous",
  "limitConcurrent": true,
  "stages": [
    {
      "name": "Wait",
      "refId": "1",
      "requisiteStageRefIds": [],
      "type": "wait",
      "waitTime": 30
    }
  ],
  "triggers": [],
  "updateTs": "1520879791608"
}
`

const missingAppJsonStr = `
{
  "name": "pipeline1",
  "id": "pipeline1",
  "keepWaitingPipelines": false,
  "lastModifiedBy": "anonymous",
  "limitConcurrent": true,
  "stages": [
    {
      "name": "Wait",
      "refId": "1",
      "requisiteStageRefIds": [],
      "type": "wait",
      "waitTime": 30
    }
  ],
  "triggers": [],
  "updateTs": "1520879791608"
}
`

// testPipelineJsonStrSaved is testPipelineJsonStr as it should appear on the wire in a save
// request: updateTs is server-owned and stripped from the submitted file. It's only carried
// forward when spin itself fetches it from an existing pipeline (see TestPipelineSave_carriesForwardUpdateTs).
const testPipelineJsonStrSaved = `
{
 "application": "app",
 "id": "pipeline1",
 "keepWaitingPipelines": false,
 "lastModifiedBy": "anonymous",
 "limitConcurrent": true,
 "name": "pipeline1",
 "parameterConfig": [
  {
   "default": "bar",
   "description": "A foo.",
   "name": "foo",
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
 "triggers": []
}
`

const testPipelineJsonStr = `
{
 "application": "app",
 "id": "pipeline1",
 "keepWaitingPipelines": false,
 "lastModifiedBy": "anonymous",
 "limitConcurrent": true,
 "name": "pipeline1",
 "parameterConfig": [
  {
   "default": "bar",
   "description": "A foo.",
   "name": "foo",
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
 "updateTs": "1520879791608"
}
`

const testPipelineYamlStr = `
name: pipeline1
id: pipeline1
application: app
keepWaitingPipelines: false
lastModifiedBy: anonymous
limitConcurrent: true
parameterConfig:
- default: bar
  description: A foo.
  name: foo
  required: true
stages:
- comments: ${ parameters.derp }
  name: Wait
  refId: "1"
  requisiteStageRefIds: []
  type: wait
  waitTime: 30
triggers: []
updateTs: "1520879791608"
`
