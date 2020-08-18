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
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

const (
	NAME  = "app"
	EMAIL = "appowner@spinnaker-test.net"
)

func TestApplicationSave_basic(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--gate-endpoint=" + ts.URL,
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testAppTaskJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestApplicationSave_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint=" + ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestApplicationSave_flags(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--gate-endpoint=" + ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := ""
	recieved := strings.TrimSpace(saveBuffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected save request body:\n%s", recieved)
	}
}

func TestApplicationSave_missingname(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint=" + ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := ""
	recieved := strings.TrimSpace(saveBuffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected save request body:\n%s", recieved)
	}
}

func TestApplicationSave_missingemail(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--application-name", NAME,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := ""
	recieved := strings.TrimSpace(saveBuffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected save request body:\n%s", recieved)
	}
}

func TestApplicationSave_missingproviders(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := ""
	recieved := strings.TrimSpace(saveBuffer.String())
	if expected != recieved {
		t.Fatalf("Unexpected save request body:\n%s", recieved)
	}
}

func TestApplicationSave_filejson(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempAppFile(testAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp app file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--file", tempFile.Name(),
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testAppTaskJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestApplicationSave_fileyaml(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempAppFile(testAppYamlStr)
	if tempFile == nil {
		t.Fatal("Could not create temp app file.")
	}
	defer os.Remove(tempFile.Name())

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--file", tempFile.Name(),
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testAppTaskJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestApplicationSave_stdinjson(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempAppFile(testAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp app file.")
	}
	defer os.Remove(tempFile.Name())

	// Prepare Stdin for test reading.
	tempFile.Seek(0, 0)
	oldStdin := os.Stdin
	defer func() { os.Stdin = oldStdin }()
	os.Stdin = tempFile

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testAppTaskJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

func TestApplicationSave_stdinyaml(t *testing.T) {
	saveBuffer := new(bytes.Buffer)
	ts := testGateAppSaveSuccess(saveBuffer)
	defer ts.Close()

	tempFile := tempAppFile(testAppYamlStr)
	if tempFile == nil {
		t.Fatal("Could not create temp app file.")
	}
	defer os.Remove(tempFile.Name())

	// Prepare Stdin for test reading.
	tempFile.Seek(0, 0)
	oldStdin := os.Stdin
	defer func() { os.Stdin = oldStdin }()
	os.Stdin = tempFile

	rootCmd, options := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewApplicationCmd(options))

	args := []string{
		"application", "save",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	expected := strings.TrimSpace(testAppTaskJsonStr)
	recieved := saveBuffer.Bytes()
	util.TestPrettyJsonDiff(t, "save request body", expected, recieved)
}

// testGateFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func testGateFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}

// testGateAppSaveSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with successful responses to pipeline execute API calls.
// Writes request body to buffer for testing.
func testGateAppSaveSuccess(buffer io.Writer) *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle(
		"/tasks",
		util.NewTestBufferHandlerFunc(http.MethodPost, buffer, http.StatusOK, strings.TrimSpace(testAppTaskRefJsonStr)),
	)
	mux.Handle("/tasks/id", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, strings.TrimSpace(testAppTaskStatusJsonStr))
	}))
	return httptest.NewServer(mux)
}

func tempAppFile(appContent string) *os.File {
	tempFile, _ := ioutil.TempFile("" /* /tmp dir. */, "app-spec")
	bytes, err := tempFile.Write([]byte(appContent))
	if err != nil || bytes == 0 {
		fmt.Println("Could not write temp file.")
		return nil
	}
	return tempFile
}

const testAppTaskRefJsonStr = `
{
 "ref": "/tasks/id"
}
`

const testAppTaskStatusJsonStr = `
{
 "status": "SUCCEEDED"
}
`

const testAppJsonStr = `
{
   "email" : "appowner@spinnaker-test.net",
   "cloudProviders" : "gce,kubernetes",
   "name" : "app",
	 "instancePort": 80
}
`

const testAppYamlStr = `
email: appowner@spinnaker-test.net
cloudProviders: gce,kubernetes
name: app
instancePort: 80
`

const testAppTaskJsonStr = `
{
 "application": "app",
 "description": "Create Application: app",
 "job": [
  {
   "application": {
    "cloudProviders": "gce,kubernetes",
    "email": "appowner@spinnaker-test.net",
    "instancePort": 80,
    "name": "app"
   },
   "type": "createApplication"
  }
 ]
}
`
