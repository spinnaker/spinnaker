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
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/canary"
	"github.com/spinnaker/spin/util"
)

func TestCanaryConfigList_basic(t *testing.T) {
	ts := testGateCanaryConfigListSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "list", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigList_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "list", "--gate-endpoint", ts.URL}
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
			w.Header().Add("content-type", "application/json")
			fmt.Fprintln(w, strings.TrimSpace(canaryConfigListJson))
		}))
	return httptest.NewServer(mux)
}

// testGateFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func testGateFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}

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
