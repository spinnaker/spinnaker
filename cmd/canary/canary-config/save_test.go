// Copyright (c) 2019, Waze, Inc.
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

package canary_config

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
	"github.com/spinnaker/spin/cmd/canary"
	"github.com/spinnaker/spin/util"
)

func TestCanaryConfigSave_createjson(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateCanaryConfigSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempCanaryConfigFile(testCanaryConfigJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp canary config file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testCanaryConfigJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestCanaryConfigSave_createyaml(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateCanaryConfigSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempCanaryConfigFile(testCanaryConfigYamlStr)
	if tempFile == nil {
		t.Fatal("Could not create temp canary config file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testCanaryConfigJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestCanaryConfigSave_update(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateCanaryConfigUpdateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempCanaryConfigFile(testCanaryConfigJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp canary config file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testCanaryConfigJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestCanaryConfigSave_stdin(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateCanaryConfigUpdateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempCanaryConfigFile(testCanaryConfigJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp canary config file.")
	}
	defer os.Remove(tempFile.Name())

	// Prepare Stdin for test reading.
	tempFile.Seek(0, 0)
	oldStdin := os.Stdin
	defer func() { os.Stdin = oldStdin }()
	os.Stdin = tempFile

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "save", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testCanaryConfigJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestCanaryConfigSave_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	tempFile := tempCanaryConfigFile(testCanaryConfigJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp canary config file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigSave_flags(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateCanaryConfigUpdateSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	// Missing canary config spec file and stdin.
	args := []string{"canary", "canary-config", "save", "--gate-endpoint", ts.URL}
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

func TestCanaryConfigSave_missingid(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateCanaryConfigUpdateSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempCanaryConfigFile(missingIdJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp canary config file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "save", "--file", tempFile.Name(), "--gate-endpoint", ts.URL}
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

func tempCanaryConfigFile(canaryConfigContent string) *os.File {
	tempFile, _ := ioutil.TempFile("" /* /tmp dir. */, "cc-spec")
	bytes, err := tempFile.Write([]byte(canaryConfigContent))
	if err != nil || bytes == 0 {
		fmt.Println("Could not write temp file.")
		return nil
	}
	return tempFile
}

// testGateCanaryConfigUpdateSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with OK to indicate a canary config exists,
// and Accepts POST calls.
// Writes request body to buffer for testing.
func testGateCanaryConfigUpdateSuccess(buffer io.Writer) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	// Return that there are no existing CCs on GET and a successful id on PUT.
	mux.Handle("/v2/canaryConfig/exampleCanaryConfigId", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPut {
			defer r.Body.Close()
			body, err := ioutil.ReadAll(r.Body)
			if err != nil {
				http.Error(w, "Failed to ready body", http.StatusInternalServerError)
				return
			}
			buffer.Write([]byte(body))

			w.Write([]byte(responseJson))
		} else {
			w.WriteHeader(http.StatusOK)
		}
	}))
	return httptest.NewServer(mux)
}

// testGateCanaryConfigSaveSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with 404 NotFound to indicate a canary config doesn't exist,
// and Accepts POST calls.
// Writes request body to buffer for testing.
func testGateCanaryConfigSaveSuccess(buffer io.Writer) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/canaryConfig", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer r.Body.Close()
		body, err := ioutil.ReadAll(r.Body)
		if err != nil {
			http.Error(w, "Failed to ready body", http.StatusInternalServerError)
			return
		}
		buffer.Write([]byte(body))

		w.Write([]byte(responseJson))
	}))
	// Return that we found no CC to signal a create.
	mux.Handle("/v2/canaryConfig/exampleCanaryConfigId", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	return httptest.NewServer(mux)
}

const responseJson = `
{
  "id": "exampleCanaryConfigId"
}
`

const missingIdJsonStr = `
{
   "applications": [
      "canaryconfigs"
   ],
   "classifier": {
      "groupWeights": {
         "Errors": 100
      },
      "scoreThresholds": {
         "marginal": 75,
         "pass": 95
      }
   },
   "configVersion": "1",
   "description": "Base canary config",
   "judge": {
      "judgeConfigurations": { },
      "name": "NetflixACAJudge-v1.0"
   },
   "metrics": [
      {
         "analysisConfigurations": {
            "canary": {
               "direction": "increase",
               "nanStrategy": "replace"
            }
         },
         "groups": [
            "Errors"
         ],
         "name": "RequestFailureRate",
         "query": {
            "crossSeriesReducer": "REDUCE_SUM",
            "customFilterTemplate": "ServiceGroupFilter",
            "groupByFields": [ ],
            "metricType": "custom.googleapis.com/server/failure_rate",
            "perSeriesAligner": "ALIGN_MEAN",
            "resourceType": "aws_ec2_instance",
            "serviceType": "stackdriver",
            "type": "stackdriver"
         },
         "scopeName": "default"
      }
   ],
   "name": "exampleCanary",
   "templates": {
      "ServiceGroupFilter": "metric.label.group_name = \"${scope}\""
   }
}
`

const testCanaryConfigJsonStr = `
{
 "applications": [
  "canaryconfigs"
 ],
 "classifier": {
  "groupWeights": {
   "Errors": 100
  },
  "scoreThresholds": {
   "marginal": 75,
   "pass": 95
  }
 },
 "configVersion": "1",
 "description": "Base canary config",
 "id": "exampleCanaryConfigId",
 "judge": {
  "judgeConfigurations": {},
  "name": "NetflixACAJudge-v1.0"
 },
 "metrics": [
  {
   "analysisConfigurations": {
    "canary": {
     "direction": "increase",
     "nanStrategy": "replace"
    }
   },
   "groups": [
    "Errors"
   ],
   "name": "RequestFailureRate",
   "query": {
    "crossSeriesReducer": "REDUCE_SUM",
    "customFilterTemplate": "ServiceGroupFilter",
    "groupByFields": [],
    "metricType": "custom.googleapis.com/server/failure_rate",
    "perSeriesAligner": "ALIGN_MEAN",
    "resourceType": "aws_ec2_instance",
    "serviceType": "stackdriver",
    "type": "stackdriver"
   },
   "scopeName": "default"
  }
 ],
 "name": "exampleCanary",
 "templates": {
  "ServiceGroupFilter": "metric.label.group_name = \"${scope}\""
 }
}
`

const testCanaryConfigYamlStr = `
applications:
- canaryconfigs
classifier:
  groupWeights:
    Errors: 100
  scoreThresholds:
    marginal: 75
    pass: 95
configVersion: '1'
description: Base canary config
id: exampleCanaryConfigId
judge:
  judgeConfigurations: {}
  name: NetflixACAJudge-v1.0
metrics:
- analysisConfigurations:
    canary:
      direction: increase
      nanStrategy: replace
  groups:
  - Errors
  name: RequestFailureRate
  query:
    crossSeriesReducer: REDUCE_SUM
    customFilterTemplate: ServiceGroupFilter
    groupByFields: []
    metricType: custom.googleapis.com/server/failure_rate
    perSeriesAligner: ALIGN_MEAN
    resourceType: aws_ec2_instance
    serviceType: stackdriver
    type: stackdriver
  scopeName: default
name: exampleCanary
templates:
  ServiceGroupFilter: metric.label.group_name = "${scope}"
`
