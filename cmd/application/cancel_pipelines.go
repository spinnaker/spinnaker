// Copyright (c) 2020, Anosua "Chini" Mukhopadhyay
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

package application

import (
	"fmt"
	// "github.com/antihax/optional"
	"github.com/spf13/cobra"
	//gate "github.com/spinnaker/spin/gateapi"
)

type cancelAllPipelinesOptions struct {
	*pipelineOptions
	getPipelinesOptions   getPipelinesOptions
	cancelPipelineOptions cancelPipelineOptions
}

var (
	cancelAllPipelinesShort   = "Cancel all the pipelines for the specified application with the specified status"
	cancelAllPipelinesLong    = "Cancel all the pipelines for the specified application with the specified status"
	cancelAllPipelinesExample = "usage: spin application pipelines cancel-all [options] id reason"
)

func NewCancelAllPipelinesCmd(pipeOptions *pipelineOptions) *cobra.Command {
	options := &cancelAllPipelinesOptions{
		pipelineOptions: pipeOptions,
		getPipelinesOptions: getPipelinesOptions{
			pipelineOptions: pipeOptions,
			expand:          false,
		},
		cancelPipelineOptions: cancelPipelineOptions{
			pipelineOptions: pipeOptions,
		},
	}

	cmd := &cobra.Command{
		Use:     "cancel-all",
		Aliases: []string{"cancel-all"},
		Short:   cancelAllPipelinesShort,
		Long:    cancelAllPipelinesLong,
		Example: cancelAllPipelinesExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cancelAllPipelines(cmd, options, args)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.getPipelinesOptions.applicationName, "application-name", "a", "", "name of the application")
	cmd.PersistentFlags().StringVarP(&options.getPipelinesOptions.status, "status", "s", "", "status pipeline to search for")
	cmd.PersistentFlags().StringVarP(&options.cancelPipelineOptions.reason, "reason", "r", "", "reason for cancelling pipeline")
	return cmd
}

func cancelAllPipelines(cmd *cobra.Command, options *cancelAllPipelinesOptions, args []string) error {
	app, err := getPipelines(cmd, &options.getPipelinesOptions, args)
	if err != nil {
		return err
	}

	if options.getPipelinesOptions.status == "" {
		options.Ui.Warn(fmt.Sprintf("No status was passed in. Cancelling all pipelines under application %s", options.getPipelinesOptions.applicationName))
	}

	for _, foundPipeline := range app {
		foundPipeline, ok := foundPipeline.(map[string]interface{})
		if !ok {
			return fmt.Errorf("foundPipeline should be an interface array")
		}
		if foundPipeline == nil {
			return fmt.Errorf("Output in NIL")
		}
		for key, value := range foundPipeline {
			if key == "id" {
				options.Ui.Info(fmt.Sprintf("Cancelling execution ID %s...", value))
				err := cancelPipelineWithID(&options.cancelPipelineOptions, fmt.Sprintf("%v", value))
				if err != nil {
					return err
				}
			}
		}
	}

	return nil
}
