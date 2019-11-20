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
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/spinnaker/spin/util"
)

func TestPipelineSave_basic(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())
	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}

	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_stdin(t *testing.T) {
	ts := GateServerSuccess()
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

	args := []string{"pipeline", "save", "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_accessdenied(t *testing.T) {
	ts := GateServerReadOnly()
	defer ts.Close()

	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}
func TestPipelineSave_flags(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	args := []string{"pipeline", "save", "--gate-endpoint", ts.URL} // Missing pipeline spec file.
	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_missingname(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	tempFile := tempPipelineFile(missingNameJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_missingid(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	tempFile := tempPipelineFile(missingIdJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineSave_missingapp(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	tempFile := tempPipelineFile(missingAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineOptions{})
	rootCmd := getRootCmdForTest()
	pipelineCmd := NewPipelineCmd(os.Stdout)
	pipelineCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
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
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, "") // Just write an empty 200 success on save.
	}))
	return httptest.NewServer(mux)
}

// GateServerReadOnly spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 OK for READ-type requests (GET), 400 otherwise.
func GateServerReadOnly() *httptest.Server {
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

// GateServerFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateServerFail() *httptest.Server {
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
