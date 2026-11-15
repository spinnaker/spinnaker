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
	cancelTaskShort   = "Cancel the specified task"
	cancelTaskLong    = "Cancel the running task with the provided id"
	cancelTaskExample = "usage: spin task cancel [options] taskId"
)

type cancelOptions struct {
	*taskOptions
}

func NewCancelCmd(taskOptions *taskOptions) *cobra.Command {
	options := &cancelOptions{
		taskOptions: taskOptions,
	}
	cmd := &cobra.Command{
		Use:     "cancel",
		Short:   cancelTaskShort,
		Long:    cancelTaskLong,
		Example: cancelTaskExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cancelTask(cmd, options, args)
		},
	}
	return cmd
}

func cancelTask(cmd *cobra.Command, options *cancelOptions, args []string) error {
	id, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}
	if id == "" {
		return errors.New("no task id supplied, exiting")
	}

	resp, err := options.GateClient.TaskControllerAPI.CancelTask(options.GateClient.Context, id).Execute()
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error canceling task %s, status code: %d\n",
			id,
			resp.StatusCode)
	}

	options.Ui.Success(fmt.Sprintf("Task %s successfully canceled", id))
	return nil
}
