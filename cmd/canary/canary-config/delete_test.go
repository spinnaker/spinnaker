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
	"github.com/spinnaker/spin/util"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

func TestCanaryConfigDelete_basic(t *testing.T) {
	ts := gateServerDeleteSuccess()
	defer ts.Close()

	args := []string{"canary-config", "delete", "configId", "--gate-endpoint", ts.URL}
	currentCmd := NewDeleteCmd()
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

func TestCanaryConfigDelete_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	args := []string{"canary-config", "delete", "configId", "--gate-endpoint", ts.URL}
	currentCmd := NewDeleteCmd()
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

func TestCanaryConfigDelete_missingid(t *testing.T) {
	ts := gateServerDeleteSuccess()
	defer ts.Close()

	args := []string{"canary-config", "delete", "--gate-endpoint", ts.URL} // Missing cc id.
	currentCmd := NewDeleteCmd()
	rootCmd := getRootCmdForTest()
	canaryConfigCmd := NewCanaryConfigCmd(os.Stdout)
	canaryConfigCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(canaryConfigCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command errantly succeeded. %s", err)
	}
}

// gateServerDeleteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to.
func gateServerDeleteSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/canaryConfig/configId", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	return httptest.NewServer(mux)
}
