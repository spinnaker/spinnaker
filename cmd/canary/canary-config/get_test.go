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
	"fmt"
	"github.com/spinnaker/spin/util"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

func TestCanaryConfigGet_basic(t *testing.T) {
	ts := testGateCanaryConfigGetSuccess()
	defer ts.Close()

	// Exclude 'canary' since we are testing only the 'canary-config' subcommand.
	args := []string{"canary-config", "get", "--id", "3f3dbcc1", "--gate-endpoint", ts.URL}
	currentCmd := NewGetCmd(canaryConfigOptions{})
	rootCmd := getRootCmdForTest()
	canaryConfigCmd := NewCanaryConfigCmd(os.Stdout)
	canaryConfigCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(canaryConfigCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigGet_args(t *testing.T) {
	ts := testGateCanaryConfigGetSuccess()
	defer ts.Close()

	// Exclude 'canary' since we are testing only the 'canary-config' subcommand.
	// Missing 'id' argument.
	args := []string{"canary-config", "get", "--gate-endpoint", ts.URL}
	currentCmd := NewGetCmd(canaryConfigOptions{})
	rootCmd := getRootCmdForTest()
	canaryConfigCmd := NewCanaryConfigCmd(os.Stdout)
	canaryConfigCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(canaryConfigCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigGet_malformed(t *testing.T) {
	ts := testGateCanaryConfigGetMalformed()
	defer ts.Close()

	// Exclude 'canary' since we are testing only the 'canary-config' subcommand.
	args := []string{"canary-config", "get", "--id", "3f3dbcc1", "--gate-endpoint", ts.URL}
	currentCmd := NewGetCmd(canaryConfigOptions{})
	rootCmd := getRootCmdForTest()
	canaryConfigCmd := NewCanaryConfigCmd(os.Stdout)
	canaryConfigCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(canaryConfigCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigGet_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	// Exclude 'canary' since we are testing only the 'canary-config' subcommand.
	args := []string{"canary-config", "get", "--id", "3f3dbcc1", "--gate-endpoint", ts.URL}
	currentCmd := NewGetCmd(canaryConfigOptions{})
	rootCmd := getRootCmdForTest()
	canaryConfigCmd := NewCanaryConfigCmd(os.Stdout)
	canaryConfigCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(canaryConfigCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineGet_notfound(t *testing.T) {
	ts := testGateCanaryConfigGetMissing()
	defer ts.Close()

	currentCmd := NewGetCmd(canaryConfigOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewCanaryConfigCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	args := []string{"canary-config", "get", "--id", "3f3dbcc1", "--gate-endpoint", ts.URL}
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
			fmt.Fprintln(w, strings.TrimSpace(canaryConfigGetJson))
		}))
	return httptest.NewServer(mux)
}

// testGateCanaryConfigGetMalformed returns a malformed get of canaryConfig configs.
func testGateCanaryConfigGetMalformed() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/v2/canaryConfig/",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			fmt.Fprintln(w, strings.TrimSpace(malformedCanaryConfigGetJson))
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

const malformedCanaryConfigGetJson = `
{{
 "applications": [
  "canaryconfigs"
 ],
 "id": "3f3dbcc1-002d-458c-b181-be4aa809922a",
 "name": "exampleCanary",
 "updatedTimestamp": 1568131247595,
 "updatedTimestampIso": "2019-09-10T16:00:47.595Z"
}
`

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
