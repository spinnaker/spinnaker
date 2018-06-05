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

package pipelines

import (
	"testing"

	"github.com/spinnaker/spin/command"
)

// TODO(jacobkiefer): This test overlaps heavily with pipeline_save_test.go,
// consider factoring common testing code out.
func TestPipelineDelete_basic(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineDelete_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineDelete_flags(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL} // Missing pipeline app and name.
	cmd := PipelineDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, flags are malformed.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineDelete_missingname(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--application", "app", "--gate-endpoint", ts.URL}
	cmd := PipelineDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, name is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineDelete_missingapp(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineDeleteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, app is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}
