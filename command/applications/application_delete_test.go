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

package applications

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spinnaker/spin/command"
)

func TestApplicationDelete_basic(t *testing.T) {
	ts := GateAppDeleteSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationDelete_fail(t *testing.T) {
	ts := GateAppDeleteFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationDelete_flags(t *testing.T) {
	ts := GateAppDeleteSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, flags are malformed.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

// GateAppDeleteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 OK.
func GateAppDeleteSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"ref": "/tasks/somethingtotallyreasonable",
		}
		b, _ := json.Marshal(&payload)
		fmt.Fprintln(w, string(b))
	}))
}

// GateAppDeleteFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateAppDeleteFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}
