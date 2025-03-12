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
	"os"
	"testing"
	"time"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/canary"
	"github.com/spinnaker/spin/util"
)

func TestCanaryConfigRetro_file(t *testing.T) {
	ts := gateServerRetroPass()
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

	args := []string{
		"canary", "canary-config", "retro",
		"--file", tempFile.Name(),
		"--control-group", "control-v000",
		"--control-location", "us-central1",
		"--experiment-group", "experiment-v000",
		"--experiment-location", "us-central1",
		"--start", "2019-09-17T17:16:02.600Z",
		"--end", "2019-09-17T18:16:02.600Z",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigRetro_stdin(t *testing.T) {
	ts := gateServerRetroPass()
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

	args := []string{
		"canary", "canary-config", "retro",
		"--control-group", "control-v000",
		"--control-location", "us-central1",
		"--experiment-group", "experiment-v000",
		"--experiment-location", "us-central1",
		"--start", "2019-09-17T17:16:02.600Z",
		"--end", "2019-09-17T18:16:02.600Z",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigRetro_fail(t *testing.T) {
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

	args := []string{
		"canary", "canary-config", "retro",
		"--file", tempFile.Name(),
		"--control-group", "control-v000",
		"--control-location", "us-central1",
		"--experiment-group", "experiment-v000",
		"--experiment-location", "us-central1",
		"--start", "2019-09-17T17:16:02.600Z",
		"--end", "2019-09-17T18:16:02.600Z",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestCanaryConfigRetro_timeout(t *testing.T) {
	retrySleepCycle = 1 * time.Millisecond

	ts := gateServerExecHang()
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

	args := []string{
		"canary", "canary-config", "retro",
		"--file", tempFile.Name(),
		"--control-group", "control-v000",
		"--control-location", "us-central1",
		"--experiment-group", "experiment-v000",
		"--experiment-location", "us-central1",
		"--start", "2019-09-17T17:16:02.600Z",
		"--end", "2019-09-17T18:16:02.600Z",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func gateServerRetroPass() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/canaries/canary", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		w.Write([]byte(canaryExecRespJson))
	}))

	mux.Handle("/v2/canaries/canary/executionId", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		w.Write([]byte(canaryFinishedPassJson))
	}))
	return httptest.NewServer(mux)
}

func gateServerExecHang() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/v2/canaries/canary", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		w.Write([]byte(canaryExecRespJson))
	}))

	mux.Handle("/v2/canaries/canary/executionId", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		w.Write([]byte(canaryUnfinishedJson))
	}))
	return httptest.NewServer(mux)
}

const canaryExecRespJson = `
{
  "canaryExecutionId": "executionId"
}
`

const canaryUnfinishedJson = `
{
  "complete": false
}
`

const canaryFinishedPassJson = `
{
  "complete": true,
  "result": {
    "judgeResult": {
      "score": {
        "classification": "PASS"
      }
    }
  }
}
`
