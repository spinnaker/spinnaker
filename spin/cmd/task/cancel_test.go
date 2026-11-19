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
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spinnaker/spinnaker/spin/cmd"
	"github.com/spinnaker/spinnaker/spin/util"
)

func cancelTaskAgainst(t *testing.T, url string, extraArgs ...string) error {
	t.Helper()
	rootCmd, rootOpts := cmd.NewCmdRoot(io.Discard, io.Discard)
	rootCmd.AddCommand(NewTaskCmd(rootOpts))

	args := append([]string{"task", "cancel", "--gate-endpoint", url}, extraArgs...)
	rootCmd.SetArgs(args)
	return rootCmd.Execute()
}

func TestTaskCancel_basic(t *testing.T) {
	canceled := ""
	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/tasks/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
			return
		}
		canceled = r.URL.Path
	}))
	ts := httptest.NewServer(mux)
	defer ts.Close()

	if err := cancelTaskAgainst(t, ts.URL, "01TASK"); err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
	if canceled != "/tasks/01TASK/cancel" {
		t.Errorf("expected task 01TASK to be canceled, got a request for %q", canceled)
	}
}

func TestTaskCancel_noinput(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	if err := cancelTaskAgainst(t, ts.URL); err == nil {
		t.Fatal("expected an error without a task id")
	}
}

func TestTaskCancel_failure(t *testing.T) {
	ts := testGateFail()
	defer ts.Close()

	if err := cancelTaskAgainst(t, ts.URL, "01TASK"); err == nil {
		t.Fatal("expected an error when gate fails")
	}
}
