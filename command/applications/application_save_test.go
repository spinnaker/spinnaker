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
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/spinnaker/spin/command"
)

const (
	NAME  = "app"
	EMAIL = "appowner@spinnaker-test.net"
)

func TestApplicationSave_basic(t *testing.T) {
	ts := testGateApplicationSaveSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationSave_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationSave_flags(t *testing.T) {
	ts := testGateApplicationSaveSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, flags are malformed.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestApplicationSave_missingname(t *testing.T) {
	ts := testGateApplicationSaveSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, name is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestApplicationSave_missingemail(t *testing.T) {
	ts := testGateApplicationSaveSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, id missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestApplicationSave_missingproviders(t *testing.T) {
	ts := testGateApplicationSaveSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, app is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestApplicationSave_filebasic(t *testing.T) {
	ts := testGateApplicationSaveSuccess()
	defer ts.Close()

	tempFile := tempAppFile(testAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp app file.")
	}
	defer os.Remove(tempFile.Name())

	meta := command.ApiMeta{}
	args := []string{
		"--file", tempFile.Name(),
		"--gate-endpoint", ts.URL,
	}

	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationSave_stdinbasic(t *testing.T) {
	ts := testGateApplicationSaveSuccess()
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

	meta := command.ApiMeta{}
	args := []string{
		"--gate-endpoint", ts.URL,
	}

	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

// GateServerFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateServerFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}

// testGatePipelineExecuteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with successful responses to pipeline execute API calls.
func testGateApplicationSaveSuccess() *httptest.Server {
	mux := http.NewServeMux()
	mux.Handle("/tasks", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"ref": "/tasks/id",
		}
		b, _ := json.Marshal(&payload)
		fmt.Fprintln(w, string(b))
	}))
	mux.Handle("/tasks/id", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"status": "SUCCEEDED",
		}
		b, _ := json.Marshal(&payload)
		fmt.Fprintln(w, string(b))
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

const testAppJsonStr = `
{
   "email" : "someone@example.com",
   "cloudProviders" : [
      "gce"
   ],
   "name" : "sampleapp"
}

`
