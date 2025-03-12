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

package application

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/andreyvit/diff"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

const (
	APP = "app"
)

func TestApplicationGet_json(t *testing.T) {
	ts := testGateApplicationGetSuccess()
	defer ts.Close()

	buffer := new(bytes.Buffer)
	rootCmd, rootOpts := cmd.NewCmdRoot(buffer, buffer)
	rootCmd.AddCommand(NewApplicationCmd(rootOpts))

	args := []string{"application", "get", APP, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(applicationJson)
	recieved := strings.TrimSpace(buffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected command output:\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestApplicationGet_jsonpath(t *testing.T) {
	ts := testGateApplicationGetSuccess()
	defer ts.Close()

	buffer := new(bytes.Buffer)
	rootCmd, rootOpts := cmd.NewCmdRoot(buffer, buffer)
	rootCmd.AddCommand(NewApplicationCmd(rootOpts))

	args := []string{"application", "get", APP, "--output", "jsonpath={.permissions}", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(permissionsJson)
	recieved := strings.TrimSpace(buffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected command output:\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestApplicationGet_yaml(t *testing.T) {
	ts := testGateApplicationGetSuccess()
	defer ts.Close()

	buffer := new(bytes.Buffer)
	rootCmd, rootOpts := cmd.NewCmdRoot(buffer, buffer)
	rootCmd.AddCommand(NewApplicationCmd(rootOpts))

	args := []string{"application", "get", APP, "--output", "yaml", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(applicationYaml)
	recieved := strings.TrimSpace(buffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected command output:\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestApplicationGet_flags(t *testing.T) {
	ts := testGateApplicationGetSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(rootOpts))

	args := []string{"application", "get", "--gate-endpoint", ts.URL} // Missing positional arg.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil { // Success is actually failure here, flags are malformed.
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestApplicationGet_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(rootOpts))

	args := []string{"application", "get", APP, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil { // Success is actually failure here, return payload is malformed.
		t.Fatalf("Command failed with: %d", err)
	}
}

// testGateApplicationGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline list.
func testGateApplicationGetSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/"+APP, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, strings.TrimSpace(applicationJsonExpanded))
	}))
	return httptest.NewServer(mux)
}

// GET /applications/{app} returns an envelope with 'attributes' and 'clusters'.
const applicationJsonExpanded = `
{
 "attributes": {
  "accounts": "account1",
  "cloudproviders": [
   "gce",
   "kubernetes"
  ],
  "createTs": "1527261941734",
  "email": "app",
  "instancePort": 80,
  "lastModifiedBy": "anonymous",
  "name": "app",
  "permissions": {
    "EXECUTE": [
	  "admin-group"
	 ],
	 "READ": [
	  "admin-group",
	  "user-group"
	 ],
	 "WRITE": [
	  "admin-group"
	 ]
  },
  "updateTs": "1527261941735",
  "user": "anonymous"
 },
 "clusters": {
  "account1": [
   {
    "loadBalancers": [],
    "name": "deployment example-deployment",
    "provider": "kubernetes",
    "serverGroups": []
   }
  ]
 }
}
`

const applicationJson = `
{
 "accounts": "account1",
 "cloudproviders": [
  "gce",
  "kubernetes"
 ],
 "createTs": "1527261941734",
 "email": "app",
 "instancePort": 80,
 "lastModifiedBy": "anonymous",
 "name": "app",
 "permissions": {
  "EXECUTE": [
   "admin-group"
  ],
  "READ": [
   "admin-group",
   "user-group"
  ],
  "WRITE": [
   "admin-group"
  ]
 },
 "updateTs": "1527261941735",
 "user": "anonymous"
}
`

const applicationYaml = `
accounts: account1
cloudproviders:
- gce
- kubernetes
createTs: "1527261941734"
email: app
instancePort: 80
lastModifiedBy: anonymous
name: app
permissions:
  EXECUTE:
  - admin-group
  READ:
  - admin-group
  - user-group
  WRITE:
  - admin-group
updateTs: "1527261941735"
user: anonymous
`

const permissionsJson = `
{
 "EXECUTE": [
  "admin-group"
 ],
 "READ": [
  "admin-group",
  "user-group"
 ],
 "WRITE": [
  "admin-group"
 ]
}
`
