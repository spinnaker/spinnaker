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
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

func TestApplicationDelete_basic(t *testing.T) {
	ts := testGateApplicationDeleteSuccess()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{"application", "delete", NAME, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestApplicationDelete_fail(t *testing.T) {
	ts := GateAppDeleteFail()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{"application", "delete", NAME, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestApplicationDelete_flags(t *testing.T) {
	ts := testGateApplicationDeleteSuccess()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{"application", "delete", NAME, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGateApplicationDeleteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with successful responses to pipeline execute API calls.
func testGateApplicationDeleteSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/"+APP, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{} // We don't use the payload, we are just checking if the target app exists.
		b, _ := json.Marshal(&payload)
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, string(b))
	}))
	mux.Handle("/tasks", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"ref": "/tasks/id",
		}
		b, _ := json.Marshal(&payload)
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, string(b))
	}))
	mux.Handle("/tasks/id", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"status": "SUCCEEDED",
		}
		b, _ := json.Marshal(&payload)
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, string(b))
	}))
	return httptest.NewServer(mux)
}

// GateAppDeleteFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateAppDeleteFail() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications/"+APP, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
	return httptest.NewServer(mux)
}
