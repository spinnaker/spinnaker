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
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
)

var (
	cancelExecutionShort = "Cancel the executions for the provided execution id"
	cancelExecutionLong  = "Cancel the executions for the provided execution id"
)

func NewCancelCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "cancel",
		Short: cancelExecutionShort,
		Long:  cancelExecutionLong,
		RunE:  cancelExecution,
	}

	return cmd
}

func cancelExecution(cmd *cobra.Command, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	executionId, err := util.ReadArgsOrStdin(args)
	if executionId == "" {
		return errors.New("no execution id supplied, exiting")
	}

	resp, err := gateClient.PipelineControllerApi.CancelPipelineUsingPUT1(gateClient.Context,
		executionId,
		map[string]interface{}{})

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("encountered an error canceling execution with id %s, status code: %d\n",
			executionId,
			resp.StatusCode)
	}

	util.UI.Info(util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Execution %s successfully canceled", executionId)))
	return nil
}
