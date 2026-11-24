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
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/spinnaker/spinnaker/spin/cmd"
	"github.com/spinnaker/spinnaker/spin/util"
)

// gateTaskServer is a fake gate that accepts task submissions and reports the submitted task's
// status, one entry of statuses per lookup with the last one repeating.
type gateTaskServer struct {
	*httptest.Server
	ref         string
	statuses    []string
	submissions int
	lookups     int
	taskBody    map[string]interface{}
}

func newGateTaskServer(ref string, statuses ...string) *gateTaskServer {
	g := &gateTaskServer{ref: ref, statuses: statuses}
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/tasks", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
			return
		}
		g.submissions++
		json.NewDecoder(r.Body).Decode(&g.taskBody)
		w.Header().Add("content-type", "application/json")
		if g.ref == "" {
			fmt.Fprintln(w, "{}")
			return
		}
		fmt.Fprintf(w, `{"ref": %q}`, g.ref)
	}))
	mux.Handle("/tasks/01TASK", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		g.lookups++
		status := "RUNNING"
		if len(g.statuses) > 0 {
			status = g.statuses[min(g.lookups-1, len(g.statuses)-1)]
		}
		w.Header().Add("content-type", "application/json")
		fmt.Fprintf(w, `{"id": "01TASK", "status": %q}`, status)
	}))
	g.Server = httptest.NewServer(mux)
	return g
}

func submitTaskAgainst(t *testing.T, url string, out io.Writer, extraArgs ...string) error {
	t.Helper()
	prevInterval := pollInterval
	t.Cleanup(func() { pollInterval = prevInterval })
	pollInterval = time.Millisecond
	rootCmd, rootOpts := cmd.NewCmdRoot(out, io.Discard)
	rootCmd.AddCommand(NewTaskCmd(rootOpts))

	args := append([]string{"task", "submit", "--gate-endpoint", url}, extraArgs...)
	rootCmd.SetArgs(args)
	return rootCmd.Execute()
}

func tempTaskFile(t *testing.T, name, content string) string {
	t.Helper()
	file := filepath.Join(t.TempDir(), name)
	if err := os.WriteFile(file, []byte(content), 0o600); err != nil {
		t.Fatalf("Could not create temp task file: %v", err)
	}
	return file
}

func TestTaskSubmit_json(t *testing.T) {
	ts := newGateTaskServer("/tasks/01TASK")
	defer ts.Close()

	out := &bytes.Buffer{}
	if err := submitTaskAgainst(t, ts.URL, out, "--file", tempTaskFile(t, "task.json", testTaskJsonStr)); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	util.TestPrettyJsonDiff(t, "task submitted to gate", strings.TrimSpace(testTaskJsonStr), mustMarshal(t, ts.taskBody))
	if ts.lookups != 0 {
		t.Errorf("expected no task lookups without --wait, got %d", ts.lookups)
	}
	if !strings.Contains(out.String(), `"ref": "/tasks/01TASK"`) {
		t.Errorf("expected the task reference in the output, got %q", out.String())
	}
}

func TestTaskSubmit_yaml(t *testing.T) {
	ts := newGateTaskServer("/tasks/01TASK")
	defer ts.Close()

	if err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.yml", testTaskYamlStr)); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	util.TestPrettyJsonDiff(t, "task submitted to gate", strings.TrimSpace(testTaskJsonStr), mustMarshal(t, ts.taskBody))
}

func TestTaskSubmit_stdin(t *testing.T) {
	ts := newGateTaskServer("/tasks/01TASK")
	defer ts.Close()

	stdin, err := os.Open(tempTaskFile(t, "task.yml", testTaskYamlStr))
	if err != nil {
		t.Fatalf("Could not open temp task file: %v", err)
	}
	defer stdin.Close()
	oldStdin := os.Stdin
	defer func() { os.Stdin = oldStdin }()
	os.Stdin = stdin

	if err := submitTaskAgainst(t, ts.URL, io.Discard); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}

	util.TestPrettyJsonDiff(t, "task submitted to gate", strings.TrimSpace(testTaskJsonStr), mustMarshal(t, ts.taskBody))
}

func TestTaskSubmit_invalidJob(t *testing.T) {
	tests := map[string]string{
		"missing job": `{"application": "app", "description": "No job"}`,
		"empty job":   `{"application": "app", "description": "No job", "job": []}`,
		"job object":  `{"application": "app", "description": "No job", "job": {"type": "wait"}}`,
	}
	for name, task := range tests {
		t.Run(name, func(t *testing.T) {
			ts := newGateTaskServer("/tasks/01TASK")
			defer ts.Close()

			err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.json", task))
			if err == nil || !strings.Contains(err.Error(), "'job'") {
				t.Fatalf("expected an error about the job, got: %v", err)
			}
			if ts.submissions != 0 {
				t.Errorf("expected nothing to be submitted, got %d submissions", ts.submissions)
			}
		})
	}
}

func TestTaskSubmit_missingFile(t *testing.T) {
	ts := newGateTaskServer("/tasks/01TASK")
	defer ts.Close()

	err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", filepath.Join(t.TempDir(), "missing.json"))
	if err == nil {
		t.Fatal("expected an error for a missing task file")
	}
	if ts.submissions != 0 {
		t.Errorf("expected nothing to be submitted, got %d submissions", ts.submissions)
	}
}

func TestTaskSubmit_fail(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.json", testTaskJsonStr))
	if err == nil {
		t.Fatal("expected an error when gate fails")
	}
}

func TestTaskSubmit_missingRef(t *testing.T) {
	ts := newGateTaskServer("")
	defer ts.Close()

	err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.json", testTaskJsonStr))
	if err == nil || !strings.Contains(err.Error(), "did not return a task reference") {
		t.Fatalf("expected a missing reference error, got: %v", err)
	}
}

func TestTaskSubmit_waitForSuccess(t *testing.T) {
	ts := newGateTaskServer("/tasks/01TASK", "NOT_STARTED", "RUNNING", "SUCCEEDED")
	defer ts.Close()

	if err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.json", testTaskJsonStr), "--wait"); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
	if ts.lookups != 3 {
		t.Errorf("expected to poll until the task succeeded (3 lookups), got %d", ts.lookups)
	}
}

func TestTaskSubmit_waitForFailure(t *testing.T) {
	for _, status := range []string{"TERMINAL", "CANCELED", "STOPPED", "SKIPPED", "FAILED_CONTINUE"} {
		t.Run(status, func(t *testing.T) {
			ts := newGateTaskServer("/tasks/01TASK", "RUNNING", status)
			defer ts.Close()

			err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.json", testTaskJsonStr), "--wait")
			if err == nil || !strings.Contains(err.Error(), "Task 01TASK finished with status "+status) {
				t.Fatalf("expected a %s failure, got: %v", status, err)
			}
			if ts.lookups != 2 {
				t.Errorf("expected to stop polling once the task completed (2 lookups), got %d", ts.lookups)
			}
		})
	}
}

func TestTaskSubmit_waitTimeout(t *testing.T) {
	ts := newGateTaskServer("/tasks/01TASK", "RUNNING")
	defer ts.Close()

	err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.json", testTaskJsonStr), "--wait", "--wait-timeout", "50ms")
	if err == nil || !strings.Contains(err.Error(), "had not completed after 50ms") {
		t.Fatalf("expected a wait timeout error, got: %v", err)
	}
}

func TestTaskSubmit_waitLookupFailure(t *testing.T) {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/tasks", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, `{"ref": "/tasks/01TASK"}`)
	}))
	mux.Handle("/tasks/01TASK", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
	ts := httptest.NewServer(mux)
	defer ts.Close()

	err := submitTaskAgainst(t, ts.URL, io.Discard, "--file", tempTaskFile(t, "task.json", testTaskJsonStr), "--wait")
	if err == nil || !strings.Contains(err.Error(), "checking on it failed") {
		t.Fatalf("expected a lookup failure, got: %v", err)
	}
}

func mustMarshal(t *testing.T, v interface{}) []byte {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("Could not marshal %v: %v", v, err)
	}
	return b
}

// testGateFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func testGateFail() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/tasks", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
	mux.Handle("/tasks/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
	return httptest.NewServer(mux)
}

const testTaskJsonStr = `
{
 "application": "app",
 "description": "Resize app-main-v001",
 "job": [
  {
   "capacity": {
    "desired": 2,
    "max": 2,
    "min": 2
   },
   "cloudProvider": "aws",
   "credentials": "my-aws-account",
   "region": "us-west-2",
   "serverGroupName": "app-main-v001",
   "type": "resizeServerGroup"
  }
 ]
}
`

const testTaskYamlStr = `
application: app
description: Resize app-main-v001
job:
- type: resizeServerGroup
  cloudProvider: aws
  credentials: my-aws-account
  region: us-west-2
  serverGroupName: app-main-v001
  capacity:
    min: 2
    max: 2
    desired: 2
`
