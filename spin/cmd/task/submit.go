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
	"errors"
	"fmt"
	"net/http"
	"path"
	"time"

	"github.com/spf13/cobra"

	"github.com/spinnaker/spinnaker/spin/util"
)

type submitOptions struct {
	*taskOptions
	taskFile    string
	wait        bool
	waitTimeout time.Duration
}

// pollInterval is how often --wait checks on the submitted task.
var pollInterval = time.Second

var (
	submitTaskShort = "Submit the provided task"
	submitTaskLong  = `Submit a task to run one or more operations outside of a pipeline.

The task is read from --file, or from STDIN, as JSON or YAML. It takes the same shape as the
body of Gate's POST /tasks: an "application", a "description", and a "job" listing the
operations (stages) to run, in order. The response, which holds a reference to the new task,
is printed so the task can be followed with "spin task get".`
	submitTaskExample = `  spin task submit --file resize.json

  where resize.json contains:

  {
    "application": "myapp",
    "description": "Resize myapp-main-v001 to 2 instances",
    "job": [
      {
        "type": "resizeServerGroup",
        "cloudProvider": "aws",
        "credentials": "my-aws-account",
        "region": "us-west-2",
        "serverGroupName": "myapp-main-v001",
        "capacity": {"min": 2, "max": 2, "desired": 2}
      }
    ]
  }`
)

func NewSubmitCmd(taskOptions *taskOptions) *cobra.Command {
	options := &submitOptions{
		taskOptions: taskOptions,
	}
	cmd := &cobra.Command{
		Use:     "submit",
		Short:   submitTaskShort,
		Long:    submitTaskLong,
		Example: submitTaskExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return submitTask(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.taskFile, "file", "f", "", "path to the task file (JSON or YAML); read from STDIN if not set")
	cmd.PersistentFlags().BoolVar(&options.wait, "wait", false, "wait for the task to complete, and fail unless it succeeds")
	cmd.PersistentFlags().DurationVar(&options.waitTimeout, "wait-timeout", 0, "with --wait, how long to wait for completion before giving up (0 waits indefinitely)")

	return cmd
}

func submitTask(cmd *cobra.Command, options *submitOptions) error {
	task, err := util.ParseJsonFromFileOrStdin(options.taskFile, false)
	if err != nil {
		return fmt.Errorf("Could not parse supplied task: %v.\n", err)
	}
	if job, ok := task["job"].([]interface{}); !ok || len(job) == 0 {
		return errors.New("The task must have a 'job' listing at least one operation to run")
	}

	ref, resp, err := options.GateClient.TaskControllerAPI.Task(options.GateClient.Context).RequestBody(task).Execute()
	if err != nil {
		return fmt.Errorf("Encountered an error submitting task: %v\n", err)
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error submitting task, status code: %d\n", resp.StatusCode)
	}

	taskRef, _ := ref["ref"].(string)
	if taskRef == "" {
		return fmt.Errorf("Gate accepted the task but did not return a task reference: %v\n", ref)
	}

	// Print the reference before waiting so the task can be followed, or canceled, from elsewhere.
	id := path.Base(taskRef)
	options.Ui.JsonOutput(ref)
	options.Ui.Success(fmt.Sprintf("Task %s submitted", id))

	if !options.wait {
		return nil
	}

	status, err := waitForTask(options, id)
	if err != nil {
		return err
	}
	if status != "SUCCEEDED" {
		return fmt.Errorf("Task %s finished with status %s\n", id, status)
	}
	options.Ui.Success(fmt.Sprintf("Task %s succeeded", id))

	return nil
}

// isTaskComplete mirrors orca's ExecutionStatus.isComplete.
func isTaskComplete(status string) bool {
	switch status {
	case "SUCCEEDED", "TERMINAL", "CANCELED", "STOPPED", "SKIPPED", "FAILED_CONTINUE":
		return true
	}
	return false
}

// waitForTask polls the task until it completes and returns its final status. A timeout of zero
// waits indefinitely.
func waitForTask(options *submitOptions, id string) (string, error) {
	var deadline time.Time
	if options.waitTimeout > 0 {
		deadline = time.Now().Add(options.waitTimeout)
	}
	for {
		task, _, err := options.GateClient.TaskControllerAPI.GetTask(options.GateClient.Context, id).Execute()
		if err != nil {
			return "", fmt.Errorf("Task %s was submitted, but checking on it failed: %v\n", id, err)
		}
		if status, _ := task["status"].(string); isTaskComplete(status) {
			return status, nil
		}
		if !deadline.IsZero() && time.Now().After(deadline) {
			return "", fmt.Errorf("Task %s was submitted but had not completed after %s\n", id, options.waitTimeout)
		}
		time.Sleep(pollInterval)
	}
}
