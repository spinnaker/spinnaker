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

package pipeline_template

import (
	"fmt"
	"io/ioutil"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/util"
)

var (
	testAppName      = "test-application"
	testPipelineName = "test-pipeline"
	testDescription  = "test-description"
	testVariables    = "one=1,two=2,three=3,four=4"
)

func TestPipelineTemplateUse_basic(t *testing.T) {
	ts := testGateVersionSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{
		"pipeline-template", "use", "test-template-id", "--application", testAppName,
		"--name", testPipelineName,
		"--description", testDescription,
		fmt.Sprintf("--set=%s", testVariables),
		"--gate-endpoint", ts.URL,
	}

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateUse_basicShort(t *testing.T) {
	ts := testGateVersionSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{
		"pipeline-template", "use", "test-template-id", "-a", testAppName,
		"-n", testPipelineName,
		"-d", testDescription,
		fmt.Sprintf("--set=%s", testVariables),
		"--gate-endpoint", ts.URL,
	}

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateUse_missingFlags(t *testing.T) {
	ts := testGateVersionSuccess()
	defer ts.Close()

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	// Missing id, application, name
	args := []string{
		"pipeline-template", "use",
		"--gate-endpoint", ts.URL,
	}
	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Expected failure but command succeeded")
	}
}

func TestPipelineTemplateUse_templateVariables(t *testing.T) {
	ts := testGateVersionSuccess()
	defer ts.Close()

	tempDir, tempFiles := createTestValuesFiles()
	defer os.RemoveAll(tempDir) // Remove all files in the test directory for use_test.go

	if tempFiles == nil || tempDir == "" {
		t.Fatal("Could not create temp pipeline template file.")
	}

	rootCmd, rootOpts := cmd.NewCmdRoot(ioutil.Discard, ioutil.Discard)
	rootCmd.AddCommand(NewPipelineTemplateCmd(rootOpts))

	args := []string{
		"pipeline-template", "use", "test-template-id", "-a", testAppName,
		"-n", testPipelineName,
		"-d", testDescription,
		"--set", testVariables,
		"--values", strings.Join(tempFiles, ","),
		fmt.Sprintf("--set=%s", testVariables),
		"--gate-endpoint", ts.URL,
	}

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

// testGateVersionSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds healthy to the version endpoint only.
func testGateVersionSuccess() *httptest.Server {
	mux := util.TestGateMuxWithVersionHandler()
	return httptest.NewServer(mux)
}

func createTestValuesFiles() (string, []string) {
	// Create temp dir for the multiple values files to sit in
	tempDir, err := ioutil.TempDir("", "use-template-tests")
	if err != nil {
		fmt.Println("Could not create temp directory")
		return "", nil
	}
	// First file content is json, second is a basic yaml file.  Yaml file should overwrite json file (where they have matching variables)
	fileContents := []string{"{\"one\":\"1\",\"two\":\"2\",\"overwrite\": false}", "overwrite: true"}
	fileNames := make([]string, len(fileContents))
	for i, valuesFile := range fileContents {
		tempFile, _ := ioutil.TempFile(tempDir /* /tmp dir. */, "template-use-values")
		bytes, err := tempFile.Write([]byte(valuesFile))
		if err != nil || bytes == 0 {
			fmt.Println("Could not write temp file.")
			return "", nil
		}
		fileNames[i] = tempFile.Name()
	}

	return tempDir, fileNames
}
