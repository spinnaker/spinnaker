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
	"fmt"
	"net/http"

	"github.com/antihax/optional"
	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

var (
	getExecutionShort = "Get the specified execution"
	getExecutionLong  = "Get the execution with the provided id "
)

type getOptions struct {
	*executionOptions
}

func NewGetCmd(executionOptions *executionOptions) *cobra.Command {
	options := &getOptions{
		executionOptions: executionOptions,
	}
	cmd := &cobra.Command{
		Use:   "get",
		Short: getExecutionShort,
		Long:  getExecutionLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getExecution(cmd, options, args)
		},
	}
	return cmd
}

func getExecution(cmd *cobra.Command, options *getOptions, args []string) error {
	id, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	query := &gate.ExecutionsControllerApiGetLatestExecutionsByConfigIdsUsingGETOpts{
		ExecutionIds: optional.NewString(id), // Status filtering is ignored when executionId is supplied
		Limit:        optional.NewInt32(1),
	}

	successPayload, resp, err := options.GateClient.ExecutionsControllerApi.GetLatestExecutionsByConfigIdsUsingGET(
		options.GateClient.Context, query)
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error getting execution %s, status code: %d\n",
			id,
			resp.StatusCode)
	}

	options.Ui.JsonOutput(successPayload)
	return nil
}
