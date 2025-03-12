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

package output

import (
	"strings"
	"testing"

	"github.com/andreyvit/diff"
)

func TestOutputMarshalToJson(t *testing.T) {
	jsonBytes, err := MarshalToJson(testMap)
	if err != nil {
		t.Fatalf("Failed to format: %s", err)
	}

	expected := strings.TrimSpace(testJsonStr)
	recieved := strings.TrimSpace(string(jsonBytes))
	if expected != recieved {
		t.Fatalf("Unexpected formatted yaml output (want- get+):\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestOutputMarshalToYaml(t *testing.T) {
	yamlBytes, err := MarshalToYaml(testMap)
	if err != nil {
		t.Fatalf("Failed to format: %s", err)
	}

	expected := strings.TrimSpace(testYamlStr)
	recieved := strings.TrimSpace(string(yamlBytes))
	if expected != recieved {
		t.Fatalf("Unexpected formatted yaml output (want- get+):\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestOutputMarshalToJsonPath_string(t *testing.T) {
	formatFunc := MarshalToJsonPathWrapper("{.parameterConfig[0].name}")
	jsonBytes, err := formatFunc(testMap)
	if err != nil {
		t.Fatalf("Failed to format: %s", err)
	}

	expected := "foo"
	recieved := string(jsonBytes)
	if recieved != expected {
		t.Fatalf("Unexpected formatted jsonpath output: want=\"%s\" got=\"%s\"", expected, recieved)
	}
}

func TestOutputMarshalToJsonPath_globString(t *testing.T) {
	formatFunc := MarshalToJsonPathWrapper("{.stages[*].name}")
	jsonBytes, err := formatFunc(testMap)
	if err != nil {
		t.Fatalf("Failed to format: %s", err)
	}

	// Expected as the current implementation only returns the first match when using a glob expression.
	expected := "Wait"
	recieved := string(jsonBytes)
	if recieved != expected {
		t.Fatalf("Unexpected formatted jsonpath output: want=\"%s\" got=\"%s\"", expected, recieved)
	}
}

func TestOutputMarshalToJsonPath_nonPrimitive(t *testing.T) {
	formatFunc := MarshalToJsonPathWrapper("{.stages}")
	jsonBytes, err := formatFunc(testMap)
	if err != nil {
		t.Fatalf("Failed to format: %s", err)
	}

	expected := strings.TrimSpace(testJsonpathNonPrimStr)
	recieved := string(jsonBytes)
	if recieved != expected {
		t.Fatalf("Unexpected formatted jsonpath output: want=\"%s\" got=\"%s\"", expected, recieved)
	}
}

var testMap = map[string]interface{}{
	"application":          "app",
	"id":                   "pipeline1",
	"keepWaitingPipelines": false,
	"lastModifiedBy":       "anonymous",
	"limitConcurrent":      true,
	"name":                 "pipeline1",
	"parameterConfig": []map[string]interface{}{
		{
			"default":     "bar",
			"description": "A foo.",
			"name":        "foo",
			"required":    true,
		},
	},
	"stages": []map[string]interface{}{
		{
			"comments":             "${ parameters.derp }",
			"name":                 "Wait",
			"refId":                "1",
			"requisiteStageRefIds": []string{},
			"type":                 "wait",
			"waitTime":             30,
		},
		{
			"comments":             "${ parameters.derp }",
			"name":                 "Wait Again",
			"refId":                "2",
			"requisiteStageRefIds": []string{},
			"type":                 "wait",
			"waitTime":             30,
		},
	},
	"triggers": []string{},
	"updateTs": "1520879791608",
}

const testJsonPathArrayOfMapsStr = `
[
 {
  "default": "bar",
  "description": "A foo.",
  "name": "foo",
  "required": true
 }
]
`

const testJsonStr = `
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
  },
  {
   "comments": "${ parameters.derp }",
   "name": "Wait Again",
   "refId": "2",
   "requisiteStageRefIds": [],
   "type": "wait",
   "waitTime": 30
  }
 ],
 "triggers": [],
 "updateTs": "1520879791608"
}
`

const testYamlStr = `
application: app
id: pipeline1
keepWaitingPipelines: false
lastModifiedBy: anonymous
limitConcurrent: true
name: pipeline1
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
- comments: ${ parameters.derp }
  name: Wait Again
  refId: "2"
  requisiteStageRefIds: []
  type: wait
  waitTime: 30
triggers: []
updateTs: "1520879791608"
`

const testJsonpathNonPrimStr = `
[
 {
  "comments": "${ parameters.derp }",
  "name": "Wait",
  "refId": "1",
  "requisiteStageRefIds": [],
  "type": "wait",
  "waitTime": 30
 },
 {
  "comments": "${ parameters.derp }",
  "name": "Wait Again",
  "refId": "2",
  "requisiteStageRefIds": [],
  "type": "wait",
  "waitTime": 30
 }
]
`
