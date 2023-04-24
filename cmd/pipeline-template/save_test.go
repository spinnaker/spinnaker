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
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

func TestPipelineTemplateSave_createjson(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateCreateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineTemplateSave_createyaml(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateCreateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateYamlStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineTemplateSave_createtag(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateCreateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--tag", "stable", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineTemplateSave_update(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateUpdateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineTemplateSave_updatetagfromfile(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	method := new(string)
	ts := testGatePipelineTemplateUpdateTagSuccess(saveBuffer, method, "stable")
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateWithTagJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateWithTagJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)

	// Verify that the commad used the tag from the file
	if *method != "update" {
		t.Fatalf("Expected 'update' request, got %s", *method)
	}
}

func TestPipelineTemplateSave_taginargumentsandfile(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	method := new(string)
	ts := testGatePipelineTemplateUpdateTagSuccess(saveBuffer, method, "test")
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateWithTagJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--tag", "test", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateWithTagJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)

	// Tag in arguments should take precedence over tag in file
	if *method != "update" {
		t.Fatalf("Expected 'update' request, got %s", *method)
	}
}

func TestPipelineTemplateSave_updatetag(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateUpdateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--tag", "stable", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineTemplateSave_stdin(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateUpdateSuccess(saveBuffer)
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

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testPipelineTemplateJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestPipelineTemplateSave_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPipelineTemplateJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateSave_flags(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateUpdateSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--gate-endpoint", ts.URL} // Missing pipeline spec file and stdin.
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

func TestPipelineTemplateSave_missingid(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateUpdateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(missingIdJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
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

func TestPipelineTemplateSave_missingschema(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGatePipelineTemplateUpdateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(missingSchemaJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
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

func tempPipelineTemplateFile(pipelineContent string) *os.File {
	tempFile, _ := ioutil.TempFile("" /* /tmp dir. */, "pipeline-spec")
	bytes, err := tempFile.Write([]byte(pipelineContent))
	if err != nil || bytes == 0 {
		fmt.Println("Could not write temp file.")
		return nil
	}
	return tempFile
}

// testGatePipelineTemplateUpdateTagSuccess spins up a local http server that we will configure the GateClient
// to direct requests to.
// Responds with OK to indicate a pipeline template with an expected tag exists.
// Responds with 404 NotFound to indicate a pipeline template with an expected tag doesn't exist.
// Accepts POST calls for create and update requests.
// Writes used method and request body to buffer for testing.
func testGatePipelineTemplateUpdateTagSuccess(buffer io.Writer, method *string, expectedTag string) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/v2/pipelineTemplates/update/testSpelTemplate",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			*method = "update"
			util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusOK, "").ServeHTTP(w, r)
		}),
	)
	mux.Handle(
		"/v2/pipelineTemplates/create",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			*method = "create"
			util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusAccepted, "").ServeHTTP(w, r)
		}),
	)
	// Return that we found an MPT if a tag from the request equals to expectedTag.
	mux.Handle("/v2/pipelineTemplates/testSpelTemplate", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("tag") == expectedTag {
			w.WriteHeader(http.StatusOK)
		} else {
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	return httptest.NewServer(mux)
}

// testGatePipelineTemplateUpdateSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with OK to indicate a pipeline template exists,
// and Accepts POST calls.
// Writes request body to buffer for testing.
func testGatePipelineTemplateUpdateSuccess(buffer io.Writer) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/v2/pipelineTemplates/update/testSpelTemplate",
		util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusOK, ""),
	)
	// Return that we found an MPT to signal that we should update.
	mux.Handle("/v2/pipelineTemplates/testSpelTemplate", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	return httptest.NewServer(mux)
}

// testGatePipelineTemplateCreateSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with 404 NotFound to indicate a pipeline template doesn't exist,
// and Accepts POST calls.
// Writes request body to buffer for testing.
func testGatePipelineTemplateCreateSuccess(buffer io.Writer) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/v2/pipelineTemplates/create",
		util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusAccepted, ""),
	)
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

const testPipelineTemplateWithTagJsonStr = `
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
 "tag": "stable",
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

const testPipelineTemplateYamlStr = `
id: testSpelTemplate
lastModifiedBy: anonymous
metadata:
  description: A generic application bake and tag pipeline.
  name: Default Bake and Tag
  owner: example@example.com
  scopes:
  - global
pipeline:
  description: ''
  keepWaitingPipelines: false
  lastModifiedBy: anonymous
  limitConcurrent: true
  notifications: []
  parameterConfig: []
  stages:
  - name: My Wait Stage
    refId: wait1
    requisiteStageRefIds: []
    type: wait
    waitTime: "${ templateVariables.waitTime }"
  triggers:
  - attributeConstraints: {}
    enabled: true
    payloadConstraints: {}
    pubsubSystem: google
    source: jake
    subscription: super-why
    subscriptionName: super-why
    type: pubsub
  updateTs: '1543509523663'
protect: false
schema: v2
updateTs: '1544475186050'
variables:
- defaultValue: 42
  description: The time a wait stage shall pauseth
  name: waitTime
  type: int
`
