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
	"errors"
	"fmt"
	"net/http"

	"github.com/antihax/optional"
	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
)

type cancelPipelineOptions struct {
	*pipelineOptions
	id     string
	reason string
}

var (
	cancelPipelinesShort   = "Cancel the pipeline for the specified pipeline execution id"
	cancelPipelinesLong    = "Cancel the pipeline for the specified pipeline execution id"
	cancelPipelinesExample = "usage: spin application pipelines cancel [options] id reason"
)

func NewCancelPipelineCmd(pipeOptions *pipelineOptions) *cobra.Command {
	options := &cancelPipelineOptions{
		pipelineOptions: pipeOptions,
	}

	cmd := &cobra.Command{
		Use:     "cancel",
		Aliases: []string{"cancel"},
		Short:   cancelPipelinesShort,
		Long:    cancelPipelinesLong,
		Example: cancelPipelinesExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cancelPipeline(cmd, options, args)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.reason, "reason", "r", "", "reason for cancelling pipeline")
	cmd.PersistentFlags().StringVarP(&options.id, "id", "i", "", "id of pipeline execution to cancel")
	return cmd
}

func cancelPipeline(cmd *cobra.Command, options *cancelPipelineOptions, args []string) error {
	err := cancelPipelineWithID(options, options.id)

	if err != nil {
		return err
	}

	return nil
}

func cancelPipelineWithID(options *cancelPipelineOptions, id string) error {
	if id == "" {
		return errors.New("execution ID must be passed in")
	}

	pipeline, resp, err := options.GateClient.ApplicationControllerApi.CancelPipelineUsingPUT(options.GateClient.Context, id, &gate.ApplicationControllerApiCancelPipelineUsingPUTOpts{Reason: optional.NewString(options.reason)})

	if resp != nil {
		switch resp.StatusCode {
		case http.StatusOK:
			// pass
		case http.StatusNotFound:
			return fmt.Errorf("Execution ID '%s' not found\n", options.id)
		default:
			return fmt.Errorf("Encountered an error getting execution ID, status code: %d\n%v", resp.StatusCode, pipeline)
		}
	}

	if err != nil {
		return err
	}

	options.Ui.Info(fmt.Sprintf("Pipeline %v was cancelled.", id))
	return nil
}
