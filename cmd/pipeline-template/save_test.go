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
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/spinnaker/spin/util"
)

func TestPipelineTemplateSave_create(t *testing.T) {
	ts := gateServerCreateSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())
	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}

	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_createtag(t *testing.T) {
	ts := gateServerCreateSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())
	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--tag", "stable", "--gate-endpoint", ts.URL}

	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_update(t *testing.T) {
	ts := gateServerUpdateSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())
	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}

	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_updatetag(t *testing.T) {
	ts := gateServerUpdateSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())
	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--tag", "stable", "--gate-endpoint", ts.URL}

	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_stdin(t *testing.T) {
	ts := gateServerUpdateSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	// Prepare Stdin for test reading.
	tempFile.Seek(0, 0)
	oldStdin := os.Stdin
	defer func() { os.Stdin = oldStdin }()
	os.Stdin = tempFile

	args := []string{"pipeline-template", "save", "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_flags(t *testing.T) {
	ts := gateServerUpdateSuccess()
	defer ts.Close()

	args := []string{"pipeline-template", "save", "--gate-endpoint", ts.URL} // Missing pipeline spec file and stdin.
	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_missingid(t *testing.T) {
	ts := gateServerUpdateSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(missingIdJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func TestPipelineTemplateSave_missingschema(t *testing.T) {
	ts := gateServerUpdateSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(missingSchemaJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	currentCmd := NewSaveCmd(pipelineTemplateOptions{})
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

func tempPipelineTemplateFile(pipelineContent string) *os.File {
	tempFile, _ := ioutil.TempFile("" /* /tmp dir. */, "pipeline-spec")
	bytes, err := tempFile.Write([]byte(pipelineContent))
	if err != nil || bytes == 0 {
		fmt.Println("Could not write temp file.")
		return nil
	}
	return tempFile
}

// gateServerUpdateSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with OK to indicate a pipeline template exists,
// and Accepts POST calls.
func gateServerUpdateSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/pipelineTemplates/update/testSpelTemplate", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			w.WriteHeader(http.StatusAccepted)
		} else {
			w.WriteHeader(http.StatusOK)
		}
	}))
	// Return that we found an MPT to signal that we should update.
	mux.Handle("/v2/pipelineTemplates/testSpelTemplate", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	return httptest.NewServer(mux)
}

// gateServerCreateSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with 404 NotFound to indicate a pipeline template doesn't exist,
// and Accepts POST calls.
func gateServerCreateSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/pipelineTemplates/create", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			w.WriteHeader(http.StatusAccepted)
		} else {
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	// Return that there are no existing MPTs.
	mux.Handle("/v2/pipelineTemplates/testSpelTemplate", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	return httptest.NewServer(mux)
}

const missingSchemaJsonStr = `
{
 "id": "testSpelTemplate",
 "lastModifiedBy": "anonymous",
 "metadata": {
  "description": "A generic application bake and tag pipeline.",
  "name": "Default Bake and Tag",
  "owner": "example@example.com",
  "scopes": [
   "global"
  ]
 },
 "pipeline": {
  "description": "",
  "keepWaitingPipelines": false,
  "lastModifiedBy": "anonymous",
  "limitConcurrent": true,
  "notifications": [],
  "parameterConfig": [],
  "stages": [
   {
    "name": "My Wait Stage",
    "refId": "wait1",
    "requisiteStageRefIds": [],
    "type": "wait",
    "waitTime": "${ templateVariables.waitTime }"
   }
  ],
  "triggers": [
   {
    "attributeConstraints": {},
    "enabled": true,
    "payloadConstraints": {},
    "pubsubSystem": "google",
    "source": "jake",
    "subscription": "super-why",
    "subscriptionName": "super-why",
    "type": "pubsub"
   }
  ],
  "updateTs": "1543509523663"
 },
 "protect": false,
 "updateTs": "1544475186050",
 "variables": [
  {
   "defaultValue": 42,
   "description": "The time a wait stage shall pauseth",
   "name": "waitTime",
   "type": "int"
  }
 ]
}
`

const missingIdJsonStr = `
{
 "lastModifiedBy": "anonymous",
 "metadata": {
  "description": "A generic application bake and tag pipeline.",
  "name": "Default Bake and Tag",
  "owner": "example@example.com",
  "scopes": [
   "global"
  ]
 },
 "pipeline": {
  "description": "",
  "keepWaitingPipelines": false,
  "lastModifiedBy": "anonymous",
  "limitConcurrent": true,
  "notifications": [],
  "parameterConfig": [],
  "stages": [
   {
    "name": "My Wait Stage",
    "refId": "wait1",
    "requisiteStageRefIds": [],
    "type": "wait",
    "waitTime": "${ templateVariables.waitTime }"
   }
  ],
  "triggers": [
   {
    "attributeConstraints": {},
    "enabled": true,
    "payloadConstraints": {},
    "pubsubSystem": "google",
    "source": "jake",
    "subscription": "super-why",
    "subscriptionName": "super-why",
    "type": "pubsub"
   }
  ],
  "updateTs": "1543509523663"
 },
 "protect": false,
 "schema": "v2",
 "updateTs": "1544475186050",
 "variables": [
  {
   "defaultValue": 42,
   "description": "The time a wait stage shall pauseth",
   "name": "waitTime",
   "type": "int"
  }
 ]
}
`

const testPipelineTemplateJsonStr = `
{
 "id": "testSpelTemplate",
 "lastModifiedBy": "anonymous",
 "metadata": {
  "description": "A generic application bake and tag pipeline.",
  "name": "Default Bake and Tag",
  "owner": "example@example.com",
  "scopes": [
   "global"
  ]
 },
 "pipeline": {
  "description": "",
  "keepWaitingPipelines": false,
  "lastModifiedBy": "anonymous",
  "limitConcurrent": true,
  "notifications": [],
  "parameterConfig": [],
  "stages": [
   {
    "name": "My Wait Stage",
    "refId": "wait1",
    "requisiteStageRefIds": [],
    "type": "wait",
    "waitTime": "${ templateVariables.waitTime }"
   }
  ],
  "triggers": [
   {
    "attributeConstraints": {},
    "enabled": true,
    "payloadConstraints": {},
    "pubsubSystem": "google",
    "source": "jake",
    "subscription": "super-why",
    "subscriptionName": "super-why",
    "type": "pubsub"
   }
  ],
  "updateTs": "1543509523663"
 },
 "protect": false,
 "schema": "v2",
 "updateTs": "1544475186050",
 "variables": [
  {
   "defaultValue": 42,
   "description": "The time a wait stage shall pauseth",
   "name": "waitTime",
   "type": "int"
  }
 ]
}
`
