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
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/andreyvit/diff"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/canary"
	"github.com/spinnaker/spin/util"
)

func TestCanaryConfigGet_json(t *testing.T) {
	ts := testGateCanaryConfigGetSuccess()
	defer ts.Close()

	buffer := new(bytes.Buffer)
	rootCmd, rootOpts := cmd.NewCmdRoot(buffer, buffer)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "get", "--id", "3f3dbcc1", "--gate-endpoint", ts.URL}

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(canaryConfigGetJson)
	recieved := strings.TrimSpace(buffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected command output:\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestCanaryConfigGet_yaml(t *testing.T) {
	ts := testGateCanaryConfigGetSuccess()
	defer ts.Close()

	buffer := new(bytes.Buffer)
	rootCmd, rootOpts := cmd.NewCmdRoot(buffer, buffer)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "get", "--id", "3f3dbcc1", "--output", "yaml", "--gate-endpoint", ts.URL}

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(canaryConfigGetYaml)
	recieved := strings.TrimSpace(buffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected command output:\n%s", diff.LineDiff(expected, recieved))
	}
}

func TestCanaryConfigGet_args(t *testing.T) {
	ts := testGateCanaryConfigGetSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	// Missing 'id' argument.
	args := []string{"canary", "canary-config", "get", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigGet_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "get", "--id", "3f3dbcc1", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_notfound(t *testing.T) {
	ts := testGateCanaryConfigGetMissing()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "get", "--id", "3f3dbcc1", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)

	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGateCanaryConfigGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed canaryConfig get.
func testGateCanaryConfigGetSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/v2/canaryConfig/",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Add("content-type", "application/json")
			fmt.Fprintln(w, strings.TrimSpace(canaryConfigGetJson))
		}))
	return httptest.NewServer(mux)
}

// testGatePipelineGetMissing returns a 404 Not Found for an errant pipeline name|application pair.
func testGateCanaryConfigGetMissing() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/canaryConfig/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
	}))
	return httptest.NewServer(mux)
}

const canaryConfigGetJson = `
{
 "applications": [
  "canaryconfigs"
 ],
 "id": "3f3dbcc1-002d-458c-b181-be4aa809922a",
 "name": "exampleCanary",
 "updatedTimestamp": 1568131247595,
 "updatedTimestampIso": "2019-09-10T16:00:47.595Z"
}
`

const canaryConfigGetYaml = `
applications:
- canaryconfigs
id: 3f3dbcc1-002d-458c-b181-be4aa809922a
name: exampleCanary
updatedTimestamp: 1568131247595
updatedTimestampIso: "2019-09-10T16:00:47.595Z"
`
