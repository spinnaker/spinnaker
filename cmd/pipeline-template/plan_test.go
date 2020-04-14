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
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

func TestPipelineTemplatePlan_basic(t *testing.T) {
	ts := testGatePlanSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPlanConfig)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "plan", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplatePlan_stdin(t *testing.T) {
	ts := testGatePlanSuccess()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPlanConfig)
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

	args := []string{"pipeline-template", "plan", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplatePlan_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	tempFile := tempPipelineTemplateFile(testPlanConfig)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline template file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "plan", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Expected failure but command succeeded")
	}
}

func TestPipelineTemplatePlan_flags(t *testing.T) {
	ts := testGatePlanSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "plan", "--gate-endpoint", ts.URL} // Missing pipeline config file and stdin.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Expected failure but command succeeded")
	}
}

// testGatePlanSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with 404 NotFound to indicate a pipeline template doesn't exist,
// and Accepts POST calls.
func testGatePlanSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/pipelineTemplates/plan", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			fmt.Fprintln(w, strings.TrimSpace(testPipelineTemplatePlanResp))
		} else {
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	return httptest.NewServer(mux)
}

const testPipelineTemplatePlanResp = `
{
   "triggers" : [
      {
         "source" : "jack",
         "enabled" : true,
         "type" : "pubsub",
         "subscription" : "super-derp",
         "subscriptionName" : "super-derp",
         "payloadConstraints" : {},
         "pubsubSystem" : "google",
         "attributeConstraints" : {}
      }
   ],
   "trigger" : {
      "artifacts" : [],
      "type" : "manual",
      "user" : "anonymous",
      "parameters" : {}
   },
   "parameterConfig" : [],
   "keepWaitingPipelines" : false,
   "description" : "",
   "stages" : [
      {
         "refId" : "wait0",
         "notifications" : [],
         "waitTime" : 2,
         "inject" : {
            "last" : false,
            "first" : true
         },
         "type" : "wait",
         "requisiteStageRefIds" : []
      },
      {
         "requisiteStageRefIds" : [
            "wait0"
         ],
         "type" : "wait",
         "waitTime" : 6,
         "refId" : "wait1",
         "name" : "My Wait Stage",
         "notifications" : []
      },
      {
         "waitTime" : 67,
         "refId" : "wait2",
         "notifications" : [],
         "requisiteStageRefIds" : [
            "wait1"
         ],
         "type" : "wait"
      }
   ],
   "limitConcurrent" : true,
   "application" : "waze",
   "lastModifiedBy" : "anonymous",
   "updateTs" : "1543509523663",
   "expectedArtifacts" : [],
   "templateVariables" : {
      "waitTime" : 6
   },
   "id" : "unknown",
   "name" : "My First SpEL Pipeline",
   "notifications" : []
}
`

const testPlanConfig = `
{
    "schema": "v2",
    "application": "app",
    "name": "My First SpEL Pipeline",
    "template": {
        "source": "spinnaker://newSpelTemplate"
    },
    "variables": {
        "waitTime": 6
    },
    "inherit": [],
    "triggers": [
        {
            "type": "pubsub",
            "enabled": true,
            "pubsubSystem": "google",
            "subscription": "super-derp",
            "subscriptionName": "super-derp",
            "source": "jack",
            "attributeConstraints": {},
            "payloadConstraints": {},
						"invalid-key": "whatever"
        }
    ],
    "parameters": [],
    "notifications": [],
    "description": "",
    "stages": [
        {
            "refId": "wait2",
            "requisiteStageRefIds": ["wait1"],
            "type": "wait",
            "waitTime": 67
        },
        {
            "refId": "wait0",
            "inject": {
                "first": true
            },
            "type": "wait",
            "waitTime": 2
        }
    ]
}
`
