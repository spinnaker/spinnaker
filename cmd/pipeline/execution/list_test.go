// Copyright (c) 2019, Google, Inc.
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

package execution

import (
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/pipeline"
	"github.com/spinnaker/spin/util"
)

func TestExecutionList_basic(t *testing.T) {
	ts := testGateExecutionListSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, pipelineOpts := pipeline.NewPipelineCmd(rootOpts)
	pipelineCmd.AddCommand(NewExecutionCmd(pipelineOpts))
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "execution", "list", "--pipeline-id", "myid", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestExecutionList_flags(t *testing.T) {
	ts := testGateExecutionListSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, pipelineOpts := pipeline.NewPipelineCmd(rootOpts)
	pipelineCmd.AddCommand(NewExecutionCmd(pipelineOpts))
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "execution", "list", "--gate-endpoint", ts.URL} // Missing pipeline id.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestExecutionList_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	pipelineCmd, pipelineOpts := pipeline.NewPipelineCmd(rootOpts)
	pipelineCmd.AddCommand(NewExecutionCmd(pipelineOpts))
	rootCmd.AddCommand(pipelineCmd)

	args := []string{"pipeline", "execution", "list", "--pipeline-id", "myid", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGateExecutionListSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed execution list.
func testGateExecutionListSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/executions/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(executionListJson))
	}))
	return httptest.NewServer(mux)
}

const executionListJson = `
[
 {
  "application": "myapp",
  "authentication": {
   "allowedAccounts": [
    "spinnaker-gce",
    "my-docker-registry",
    "default"
   ],
   "user": "anonymous"
  },
  "buildTime": 1550860667806,
  "canceled": false,
  "endTime": 1550860701046,
  "id": "01D4B7ZJWYANYN45AWMK4RNTYN",
  "initialConfig": {},
  "keepWaitingPipelines": false,
  "limitConcurrent": true,
  "name": "param wait",
  "notifications": [],
  "origin": "deck",
  "pipelineConfigId": "d34db81c-b2f1-4748-b660-609a77945d51",
  "stages": [
   {
    "context": {
     "comments": "2019-03-02",
     "startTime": 1550860670135,
     "waitTime": "30"
    },
    "endTime": 1550860700773,
    "id": "01D4B7ZJWYK838WRP9PD44KKT3",
    "name": "Wait",
    "outputs": {},
    "refId": "1",
    "requisiteStageRefIds": [
     "2"
    ],
    "startTime": 1550860669658,
    "status": "SUCCEEDED",
    "tasks": [
     {
      "endTime": 1550860700539,
      "id": "1",
      "implementingClass": "com.netflix.spinnaker.orca.pipeline.tasks.WaitTask",
      "loopEnd": false,
      "loopStart": false,
      "name": "wait",
      "stageEnd": true,
      "stageStart": true,
      "startTime": 1550860669893,
      "status": "SUCCEEDED"
     }
    ],
    "type": "wait"
   },
   {
    "context": {
     "failOnFailedExpressions": true,
     "variables": [
      {
       "key": "theDate",
       "value": "2019-03-02"
      }
     ]
    },
    "endTime": 1550860669437,
    "id": "01D4B7ZJWY2WRC7K1QGKS6DQXF",
    "name": "Evaluate Variables",
    "outputs": {
     "theDate": "2019-03-02"
    },
    "refId": "2",
    "requisiteStageRefIds": [],
    "startTime": 1550860668515,
    "status": "SUCCEEDED",
    "tasks": [
     {
      "endTime": 1550860669203,
      "id": "1",
      "implementingClass": "com.netflix.spinnaker.orca.pipeline.tasks.EvaluateVariablesTask",
      "loopEnd": false,
      "loopStart": false,
      "name": "evaluateVariables",
      "stageEnd": true,
      "stageStart": true,
      "startTime": 1550860668749,
      "status": "SUCCEEDED"
     }
    ],
    "type": "evaluateVariables"
   }
  ],
  "startTime": 1550860668270,
  "status": "SUCCEEDED",
  "systemNotifications": [],
  "trigger": {
   "artifacts": [],
   "dryRun": false,
   "notifications": [],
   "parameters": {},
   "rebake": false,
   "resolvedExpectedArtifacts": [],
   "strategy": false,
   "type": "manual",
   "user": "anonymous"
  },
  "type": "PIPELINE"
 }
]
`
