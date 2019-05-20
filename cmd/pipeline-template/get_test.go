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

package pipeline_template

import (
"fmt"
"net/http"
"net/http/httptest"
"os"
"strings"
"testing"

"github.com/spf13/cobra"
"github.com/spinnaker/spin/util"
)

func getRootCmdForTest() *cobra.Command {
	rootCmd := &cobra.Command{}
	rootCmd.PersistentFlags().String("config", "", "config file (default is $HOME/.spin/config)")
	rootCmd.PersistentFlags().String("gate-endpoint", "", "Gate (API server) endpoint. Default http://localhost:8084")
	rootCmd.PersistentFlags().Bool("insecure", false, "Ignore Certificate Errors")
	rootCmd.PersistentFlags().Bool("quiet", false, "Squelch non-essential output")
	rootCmd.PersistentFlags().Bool("no-color", false, "Disable color")
	rootCmd.PersistentFlags().String("output", "", "Configure output formatting")
	rootCmd.PersistentFlags().String("default-headers", "", "Configure addtional headers for gate client requests")
	util.InitUI(false, false, "")
	return rootCmd
}

func TestPipelineGet_basic(t *testing.T) {
	ts := testGatePipelineTemplateGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"pipeline-template", "get", "--id", "newSpelTemplate", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_args(t *testing.T) {
	ts := testGatePipelineTemplateGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"pipeline-template", "get", "newSpelTemplate", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_tag(t *testing.T) {
	ts := testGatePipelineTemplateGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"pipeline-template", "get", "newSpelTemplate", "--tag", "stable", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_flags(t *testing.T) {
	ts := testGatePipelineTemplateGetSuccess()
	defer ts.Close()

	currentCmd := NewGetCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"pipeline-template", "get", "--gate-endpoint", ts.URL} // missing id flag and no args
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_malformed(t *testing.T) {
	ts := testGatePipelineTemplateGetMalformed()
	defer ts.Close()

	currentCmd := NewGetCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"pipeline-template", "get", "--id", "newSpelTemplate", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	currentCmd := NewGetCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"pipeline-template", "get", "--id", "newSpelTemplate", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_notfound(t *testing.T) {
	ts := testGatePipelineTemplateGetMissing()
	defer ts.Close()

	currentCmd := NewGetCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"pipeline-template", "get", "--application", "app", "--name", "two", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGatePipelineGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline get response.
func testGatePipelineTemplateGetSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(pipelineTemplateGetJson))
	}))
}

// testGatePipelineGetMalformed returns a malformed get response of pipeline configs.
func testGatePipelineTemplateGetMalformed() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedPipelineTemplateGetJson))
	}))
}

// testGatePipelineGetMissing returns a 404 Not Found for an errant pipeline name|application pair.
func testGatePipelineTemplateGetMissing() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
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

const malformedPipelineTemplateGetJson = `
 "id": "newSpelTemplate",
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
    "source": "jtk54",
    "subscription": "super-pub",
    "subscriptionName": "super-pub",
    "type": "pubsub"
   }
  ],
  "updateTs": "1543509523663"
 },
 "protect": false,
 "schema": "v2",
 "updateTs": "1543860678988",
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

const pipelineTemplateGetJson = `
{
 "id": "newSpelTemplate",
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
    "source": "jtk54",
    "subscription": "super-pub",
    "subscriptionName": "super-pub",
    "type": "pubsub"
   }
  ],
  "updateTs": "1543509523663"
 },
 "protect": false,
 "schema": "v2",
 "updateTs": "1543860678988",
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
