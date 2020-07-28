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

package pipeline

import (
	"errors"
	"fmt"
	"net/http"

	"github.com/spf13/cobra"
)

type listOptions struct {
	*PipelineOptions
	output      string
	application string
}

var (
	listPipelineShort = "List the pipelines for the provided application"
	listPipelineLong  = "List the pipelines for the provided application"
)

func NewListCmd(pipelineOptions *PipelineOptions) *cobra.Command {
	options := &listOptions{
		PipelineOptions: pipelineOptions,
	}
	cmd := &cobra.Command{
		Use:     "list",
		Aliases: []string{"ls"},
		Short:   listPipelineShort,
		Long:    listPipelineLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return listPipeline(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.application, "application", "a", "", "Spinnaker application to list pipelines from")

	return cmd
}

func listPipeline(cmd *cobra.Command, options *listOptions) error {
	if options.application == "" {
		return errors.New("required parameter 'application' not set")
	}

	successPayload, resp, err := options.GateClient.ApplicationControllerApi.GetPipelineConfigsForApplicationUsingGET(options.GateClient.Context, options.application)
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error listing pipelines for application %s, status code: %d\n",
			options.application,
			resp.StatusCode)
	}

	options.Ui.JsonOutput(successPayload)
	return nil
}
