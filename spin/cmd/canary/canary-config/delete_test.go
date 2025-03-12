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
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/canary"
	"github.com/spinnaker/spin/util"
)

func TestCanaryConfigDelete_basic(t *testing.T) {
	ts := testGateDeleteSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "delete", "configId", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigDelete_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "delete", "configId", "--gate-endpoint", ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigDelete_missingid(t *testing.T) {
	ts := testGateDeleteSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	args := []string{"canary", "canary-config", "delete", "--gate-endpoint", ts.URL} // Missing cc id.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command errantly succeeded. %s", err)
	}
}

// testGateDeleteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to.
func testGateDeleteSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/canaryConfig/configId", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	return httptest.NewServer(mux)
}
