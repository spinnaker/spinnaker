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
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

func TestApplicationList_basic(t *testing.T) {
	ts := testGateApplicationList()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{"application", "list", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestApplicationList_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{"application", "list", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGateApplicationList spins up a local http server that we will configure the GateClient
// to direct requests to. When 'returnMalformed' is false, responds with a 200 and a well-formed application list.
// Returns a malformed list of application configs when 'returnMalformed' is true
func testGateApplicationList() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/applications", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, strings.TrimSpace(applicationListJson))
	}))

	return httptest.NewServer(mux)
}

const applicationListJson = `
[
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
    "updateTs": "1527261941735",
    "user": "anonymous"
  }
]
`
