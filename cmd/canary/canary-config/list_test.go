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
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/util"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

func getRootCmdForTest() *cobra.Command {
	rootCmd := &cobra.Command{}
	rootCmd.PersistentFlags().String("config", "", "config file (default is $HOME/.spin/config)")
	rootCmd.PersistentFlags().String("gate-endpoint", "", "Gate (API server) endpoint. Default http://localhost:8084")
	rootCmd.PersistentFlags().Bool("insecure", false, "Ignore Certificate Errors")
	rootCmd.PersistentFlags().Bool("quiet", false, "Squelch non-essential output")
	rootCmd.PersistentFlags().Bool("no-color", false, "Disable color")
	rootCmd.PersistentFlags().String("output", "", "Configure output formatting")
	rootCmd.PersistentFlags().String("default-headers", "", "Configure additional headers for gate client requests")
	util.InitUI(false, false, "")
	return rootCmd
}

func TestCanaryConfigList_basic(t *testing.T) {
	ts := testGateCanaryConfigListSuccess()
	defer ts.Close()

	// Exclude 'canary' since we are testing only the 'canary-config' subcommand.
	args := []string{"canary-config", "list", "--gate-endpoint", ts.URL}
	currentCmd := NewListCmd(canaryConfigOptions{})
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

func TestCanaryConfigList_malformed(t *testing.T) {
	ts := testGateCanaryConfigListMalformed()
	defer ts.Close()

	// Exclude 'canary' since we are testing only the 'canary-config' subcommand.
	args := []string{"canary-config", "list", "--gate-endpoint", ts.URL}
	currentCmd := NewListCmd(canaryConfigOptions{})
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

func TestCanaryConfigList_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	// Exclude 'canary' since we are testing only the 'canary-config' subcommand.
	args := []string{"canary-config", "list", "--gate-endpoint", ts.URL}
	currentCmd := NewListCmd(canaryConfigOptions{})
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

// testGateCanaryConfigListSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed canaryConfig list.
func testGateCanaryConfigListSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/v2/canaryConfig",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			fmt.Fprintln(w, strings.TrimSpace(canaryConfigListJson))
		}))
	return httptest.NewServer(mux)
}

// testGateCanaryConfigListMalformed returns a malformed list of canaryConfig configs.
func testGateCanaryConfigListMalformed() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/v2/canaryConfig",
		http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			fmt.Fprintln(w, strings.TrimSpace(malformedCanaryConfigListJson))
		}))
	return httptest.NewServer(mux)
}

// GateServerFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateServerFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}

const gateVersionJson = `
{
  "version": "1.0.0"
}
`

const malformedCanaryConfigListJson = `
 {
  "applications": [
   "canaryconfigs"
  ],
  "id": "3f3dbcc1-002d-458c-b181-be4aa809922a",
  "name": "exampleCanary",
  "updatedTimestamp": 1568131247595,
  "updatedTimestampIso": "2019-09-10T16:00:47.595Z"
 }
]
`

const canaryConfigListJson = `
[
 {
  "applications": [
   "canaryconfigs"
  ],
  "id": "3f3dbcc1-002d-458c-b181-be4aa809922a",
  "name": "exampleCanary",
  "updatedTimestamp": 1568131247595,
  "updatedTimestampIso": "2019-09-10T16:00:47.595Z"
 }
]
`
