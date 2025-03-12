// Copyright (c) 2019, Google, Inc.
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

package execution

import (
	"errors"
	"fmt"
	"net/http"

	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

var (
	cancelExecutionShort = "Cancel the executions for the provided execution id"
	cancelExecutionLong  = "Cancel the executions for the provided execution id"
)

type cancelOptions struct {
	*executionOptions
}

func NewCancelCmd(executionOptions *executionOptions) *cobra.Command {
	options := &cancelOptions{
		executionOptions: executionOptions,
	}
	cmd := &cobra.Command{
		Use:   "cancel",
		Short: cancelExecutionShort,
		Long:  cancelExecutionLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cancelExecution(cmd, options, args)
		},
	}

	return cmd
}

func cancelExecution(cmd *cobra.Command, options *cancelOptions, args []string) error {
	executionId, err := util.ReadArgsOrStdin(args)
	if executionId == "" {
		return errors.New("no execution id supplied, exiting")
	}

	resp, err := options.GateClient.PipelineControllerApi.CancelPipelineUsingPUT1(options.GateClient.Context,
		executionId,
		&gate.PipelineControllerApiCancelPipelineUsingPUT1Opts{})
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("encountered an error canceling execution with id %s, status code: %d\n",
			executionId,
			resp.StatusCode)
	}

	options.Ui.Success(fmt.Sprintf("Execution %s successfully canceled", executionId))
	return nil
}
