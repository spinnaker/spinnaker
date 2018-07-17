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
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/spinnaker/spin/command"
)

func TestPipelineSave_basic(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	cmd := PipelineSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineSave_stdin(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
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

	args := []string{"--gate-endpoint", ts.URL}
	cmd := PipelineSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineSave_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	cmd := PipelineSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineSave_flags(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL} // Missing pipeline spec file.
	cmd := PipelineSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, flags are malformed.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineSave_missingname(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(missingNameJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	cmd := PipelineSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, name is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineSave_missingid(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(missingIdJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	cmd := PipelineSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, id missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineSave_missingapp(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(missingAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	cmd := PipelineSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, app is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
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

// GateServerSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 OK.
func GateServerSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, "") // Just write an empty 200 success on save.
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

const testPipelineJsonStr = `
{
  "name": "pipeline1",
  "id": "pipeline1",
  "application": "app",
  "keepWaitingPipelines": false,
  "lastModifiedBy": "anonymous",
  "limitConcurrent": true,
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
