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

type getOptions struct {
	*PipelineOptions
	output      string
	application string
	name        string
}

var (
	getPipelineShort = "Get the pipeline with the provided name from the provided application"
	getPipelineLong  = "Get the specified pipeline"
)

func NewGetCmd(pipelineOptions *PipelineOptions) *cobra.Command {
	options := &getOptions{
		PipelineOptions: pipelineOptions,
	}
	cmd := &cobra.Command{
		Use:   "get",
		Short: getPipelineShort,
		Long:  getPipelineLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getPipeline(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.application, "application", "a", "", "Spinnaker application the pipeline belongs to")
	cmd.PersistentFlags().StringVarP(&options.name, "name", "n", "", "name of the pipeline")

	return cmd
}

func getPipeline(cmd *cobra.Command, options *getOptions) error {
	if options.application == "" || options.name == "" {
		return errors.New("one of required parameters 'application' or 'name' not set")
	}

	successPayload, resp, err := options.GateClient.ApplicationControllerApi.GetPipelineConfigUsingGET(options.GateClient.Context,
		options.application,
		options.name)
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error getting pipeline in pipeline %s with name %s, status code: %d\n",
			options.application,
			options.name,
			resp.StatusCode)
	}

	options.Ui.JsonOutput(successPayload)
	return nil
}
