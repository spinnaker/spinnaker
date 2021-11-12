// Copyright 2021, Han Byul Ko.
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
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

func TestAccountWhoami_basic(t *testing.T) {
	ts := testAccountWhoamiSuccess()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewAccountCmd(options))

	args := []string{"account", "whoami", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestAccountWhoami_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewAccountCmd(options))

	args := []string{"account", "whoami", "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func testAccountWhoamiSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/auth/user", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, strings.TrimSpace(whoamiJson))
	}))
	return httptest.NewServer(mux)
}

const whoamiJson = `[
{
	"email": "pcmusic@example.com",
	"username": "pcmusic@example.com",
	"firstName": "A. G.",
	"lastName": "Cook",
	"roles": [
		"public",
		"admin"
	],
	"allowedAccounts": [
		"ecr",
		"spinnaker"
	],
	"enabled": true,
	"authorities": [
		{
			"authority": "public"
		},
		{
			"authority": "admin"
		}
	],
	"accountNonExpired": true,
	"credentialsNonExpired": true,
	"accountNonLocked": true
}
]
`
