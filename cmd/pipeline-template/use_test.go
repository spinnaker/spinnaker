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
	"os"
	"strings"
	"testing"
)

var testAppName = "test-application"
var testPipelineName = "test-pipeline"
var testDescription = "test-description"
var testVariables = "one=1,two=2,three=3,four=4"

func TestPipelineTemplateUse_basic(t *testing.T) {
	args := []string{"pipeline-template", "use", "test-template-id", "--application", testAppName,
		"--name", testPipelineName,
		"--description", testDescription,
		fmt.Sprintf("--set=%s", testVariables)}

	currentCmd := NewUseCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateUse_basicShort(t *testing.T) {
	args := []string{"pipeline-template", "use", "test-template-id", "-a", testAppName,
		"-n", testPipelineName,
		"-d", testDescription,
		fmt.Sprintf("--set=%s", testVariables)}

	currentCmd := NewUseCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
}

func TestPipelineTemplateUse_missingFlags(t *testing.T) {
	args := []string{"pipeline-template", "use"} // Missing id, application, name
	currentCmd := NewUseCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err == nil {
		t.Fatalf("Expected failure but command succeeded")
	}
}

func TestPipelineTemplateUse_templateVariables(t *testing.T) {
	tempDir, tempFiles := createTestValuesFiles()
	defer os.RemoveAll(tempDir) // Remove all files in the test directory for use_test.go

	if tempFiles == nil || tempDir == "" {
		t.Fatal("Could not create temp pipeline template file.")
	}

	args := []string{"pipeline-template", "use", "test-template-id", "-a", testAppName,
		"-n", testPipelineName,
		"-d", testDescription,
		"--set", testVariables,
		"--values", strings.Join(tempFiles, ","),
		fmt.Sprintf("--set=%s", testVariables)}

	currentCmd := NewPlanCmd(pipelineTemplateOptions{})
	rootCmd := getRootCmdForTest()
	pipelineTemplateCmd := NewPipelineTemplateCmd(os.Stdout)
	pipelineTemplateCmd.AddCommand(currentCmd)
	rootCmd.AddCommand(pipelineTemplateCmd)

	rootCmd.SetArgs(args)
	err := rootCmd.Execute()
	if err != nil {
		t.Fatalf("Command failed with: %s", err)
	}
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
