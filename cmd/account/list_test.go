// Copyright 2019 New Relic Corporation. All rights reserved.
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

package account

import (
	"fmt"
	"github.com/spinnaker/spin/util"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

func TestAccountList_basic(t *testing.T) {
	ts := testGateAccountListSuccess()
	defer ts.Close()

	currentCmd := NewListCmd(accountOptions{})
	rootCmd := getRootCmdForTest()
	accCmd := NewAccountCmd(os.Stdout)
	accCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(accCmd)

	args := []string{"account", "list", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestAccountList_malformed(t *testing.T) {
	ts := testGateAccountListMalformed()
	defer ts.Close()

	currentCmd := NewListCmd(accountOptions{})
	rootCmd := getRootCmdForTest()
	accCmd := NewAccountCmd(os.Stdout)
	accCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(accCmd)

	args := []string{"account", "list", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestAccountList_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	currentCmd := NewListCmd(accountOptions{})
	rootCmd := getRootCmdForTest()
	accCmd := NewAccountCmd(os.Stdout)
	accCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(accCmd)

	args := []string{"account", "list", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func testGateAccountListSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/credentials/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(accountListJson))
	}))
	return httptest.NewServer(mux)
}

func testGateAccountListMalformed() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/credentials/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedAccountListJson))
	}))
	return httptest.NewServer(mux)
}

const malformedAccountListJson = `
	{
	  "type": "kubernetes",
	  "skin": "v2",
	  "providerVersion": "v2",
	  "name": "foobar"
	 },
	 {
	  "type": "dockerRegistry",
	  "skin": "v1",
	  "providerVersion": "v1",
	  "name": "dockerhub"
	 }
]
`

const accountListJson = `[
{
  "type": "kubernetes",
  "skin": "v2",
  "providerVersion": "v2",
  "name": "foobar"
 },
 {
  "type": "dockerRegistry",
  "skin": "v1",
  "providerVersion": "v1",
  "name": "dockerhub"
 }
]
`
