// Copyright (c) 2026, Spinnaker Authors.
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

package task

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spinnaker/spinnaker/spin/cmd"
	"github.com/spinnaker/spinnaker/spin/util"
)

func getTaskAgainst(t *testing.T, url string, out io.Writer, extraArgs ...string) error {
	t.Helper()
	rootCmd, rootOpts := cmd.NewCmdRoot(out, io.Discard)
	rootCmd.AddCommand(NewTaskCmd(rootOpts))

	args := append([]string{"task", "get", "--gate-endpoint", url}, extraArgs...)
	rootCmd.SetArgs(args)
	return rootCmd.Execute()
}

func TestTaskGet_basic(t *testing.T) {
	ts := testGateTaskGetSuccess()
	defer ts.Close()

	out := &bytes.Buffer{}
	if err := getTaskAgainst(t, ts.URL, out, "01TASK"); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
	util.TestPrettyJsonDiff(t, "task output", strings.TrimSpace(testTaskGetJsonStr), out.Bytes())
}

func TestTaskGet_noinput(t *testing.T) {
	ts := testGateTaskGetSuccess()
	defer ts.Close()

	if err := getTaskAgainst(t, ts.URL, io.Discard); err == nil {
		t.Fatal("expected an error without a task id")
	}
}

func TestTaskGet_notFound(t *testing.T) {
	ts := testGateTaskGetSuccess()
	defer ts.Close()

	err := getTaskAgainst(t, ts.URL, io.Discard, "missing")
	if err == nil || !strings.Contains(err.Error(), "Task 'missing' not found") {
		t.Fatalf("expected a not found error, got: %v", err)
	}
}

func TestTaskGet_failure(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	if err := getTaskAgainst(t, ts.URL, io.Discard, "01TASK"); err == nil {
		t.Fatal("expected an error when gate fails")
	}
}

// testGateTaskGetSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 and a well-formed task for 01TASK, and a 404 for
// any other task.
func testGateTaskGetSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/tasks/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/tasks/01TASK" {
			http.Error(w, `{"message": "Task not found"}`, http.StatusNotFound)
			return
		}
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, strings.TrimSpace(testTaskGetJsonStr))
	}))
	return httptest.NewServer(mux)
}

const testTaskGetJsonStr = `
{
 "application": "app",
 "id": "01TASK",
 "name": "Resize app-main-v001",
 "stages": [
  {
   "id": "01STAGE",
   "status": "SUCCEEDED",
   "type": "resizeServerGroup"
  }
 ],
 "status": "SUCCEEDED"
}
`
