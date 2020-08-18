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
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

func TestPipelineTemplateList_basic(t *testing.T) {
	ts := testGatePipelineTemplateListSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "list", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateList_scope(t *testing.T) {
	ts := testGateScopedPipelineTemplateListSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "list", "--scopes", "specific", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateList_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{"pipeline-template", "list", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGatePipelineTemplateListSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipelineTemplate list.
func testGatePipelineTemplateListSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/pipelineTemplates/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(pipelineTemplateListJson))
	}))
	return httptest.NewServer(mux)
}

// testGateScopedPipelineTemplateListSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipelineTemplate list.
func testGateScopedPipelineTemplateListSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/pipelineTemplates/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(scopedPipelineTemplateListJson))
	}))
	return httptest.NewServer(mux)
}

const pipelineTemplateListJson = `
[
  {
   "id": "newSpelTemplate",
   "lastModifiedBy": "anonymous",
   "metadata": {
    "description": "A generic application wait.",
    "name": "Default Wait",
    "owner": "example@example.com",
    "scopes": [
     "specific"
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
   "updateTs": "1543860678988",
   "variables": [
    {
     "defaultValue": 42,
     "description": "The time a wait stage shall pauseth",
     "name": "waitTime",
     "type": "int"
    }
   ]
  },
  {
   "id": "newSpelTemplate",
   "lastModifiedBy": "anonymous",
   "metadata": {
    "description": "A generic application wait.",
    "name": "Default Wait",
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
]
`

const scopedPipelineTemplateListJson = `
[
  {
   "id": "newSpelTemplate",
   "lastModifiedBy": "anonymous",
   "metadata": {
    "description": "A generic application wait.",
    "name": "Default Wait",
    "owner": "example@example.com",
    "scopes": [
     "specific"
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
]
`
