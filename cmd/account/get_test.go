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
	rootCmd.PersistentFlags().String("default-headers", "", "Configure addtional headers for gate client requests")
	util.InitUI(false, false, "")
	return rootCmd
}

// GateServerFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateServerFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}

const (
	ACCOUNT = "account"
)

func TestAccountGet_basic(t *testing.T) {
	ts := testGateAccountGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd(accountOptions{})
	rootCmd := getRootCmdForTest()
	accCmd := NewAccountCmd(os.Stdout)
	accCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(accCmd)

	args := []string{"account", "get", ACCOUNT, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestAccountGet_flags(t *testing.T) {
	ts := testGateAccountGetSuccess()
	defer ts.Close()
	currentCmd := NewGetCmd(accountOptions{})
	rootCmd := getRootCmdForTest()
	accCmd := NewAccountCmd(os.Stdout)
	accCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(accCmd)
	args := []string{"account", "get", "--gate-endpoint", ts.URL} // Missing positional arg.
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil { // Success is actually failure here, flags are malformed.
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestAccountGet_malformed(t *testing.T) {
	ts := testGateAccountGetMalformed()
	defer ts.Close()

	currentCmd := NewGetCmd(accountOptions{})
	rootCmd := getRootCmdForTest()
	accCmd := NewAccountCmd(os.Stdout)
	accCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(accCmd)

	args := []string{"account", "get", ACCOUNT, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil { // Success is actually failure here, return payload is malformed.
		t.Fatalf("Command failed with: %d", err)
	}
}

func TestAccountGet_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	currentCmd := NewGetCmd(accountOptions{})
	rootCmd := getRootCmdForTest()
	accCmd := NewAccountCmd(os.Stdout)
	accCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(accCmd)

	args := []string{"account", "get", ACCOUNT, "--gate-endpoint=" + ts.URL}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil { // Success is actually failure here, return payload is malformed.
		t.Fatalf("Command failed with: %d", err)
	}
}

// testGateAccountGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed pipeline list.
func testGateAccountGetSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(accountJson))
	}))
}

// testGateAccountGetMalformed returns a malformed list of pipeline configs.
func testGateAccountGetMalformed() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, strings.TrimSpace(malformedAccountGetJson))
	}))
}

const malformedAccountGetJson = `
 "type": "kubernetes",
 "providerVersion": "v2",
 "environment": "self",
 "skin": "v2",
 "name": "self",
 "cloudProvider": "kubernetes",
 "accountType": "self"
}
`

const accountJson = `
{
 "type": "kubernetes",
 "providerVersion": "v2",
 "environment": "self",
 "skin": "v2",
 "name": "account",
 "cloudProvider": "kubernetes",
 "accountType": "self"
}
`
