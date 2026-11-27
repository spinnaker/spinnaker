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

	"github.com/spf13/cobra"

	"github.com/spinnaker/spinnaker/spin/util"
)

var (
	getTaskShort   = "Get the specified task"
	getTaskLong    = "Get the task with the provided id, including its status, stages and outputs"
	getTaskExample = "usage: spin task get [options] taskId"
)

type getOptions struct {
	*taskOptions
}

func NewGetCmd(taskOptions *taskOptions) *cobra.Command {
	options := &getOptions{
		taskOptions: taskOptions,
	}
	cmd := &cobra.Command{
		Use:     "get",
		Short:   getTaskShort,
		Long:    getTaskLong,
		Example: getTaskExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getTask(cmd, options, args)
		},
	}
	return cmd
}

func getTask(cmd *cobra.Command, options *getOptions, args []string) error {
	id, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}
	if id == "" {
		return errors.New("no task id supplied, exiting")
	}

	task, resp, err := options.GateClient.TaskControllerAPI.GetTask(options.GateClient.Context, id).Execute()
	if resp != nil && resp.StatusCode == http.StatusNotFound {
		return fmt.Errorf("Task '%s' not found\n", id)
	}
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error getting task %s, status code: %d\n",
			id,
			resp.StatusCode)
	}

	options.Ui.JsonOutput(task)
	return nil
}
