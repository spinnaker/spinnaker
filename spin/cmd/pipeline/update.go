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

package pipeline

import (
	"fmt"
	"net/http"

	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
)

type updateOptions struct {
	*PipelineOptions
	disabled    bool
	enabled     bool
	application string
	name        string
}

const (
	updatePipelineShort = "Update the provided pipeline"
	updatePipelineLong  = "Update the provided pipeline"
)

// NewUpdateCmd sets flags and options for the pipeline update command
func NewUpdateCmd(pipelineOptions *PipelineOptions) *cobra.Command {
	options := &updateOptions{
		PipelineOptions: pipelineOptions,
	}
	cmd := &cobra.Command{
		Use:     "update",
		Aliases: []string{},
		Short:   updatePipelineShort,
		Long:    updatePipelineLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return updatePipeline(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.application, "application", "a", "", "Spinnaker application the pipeline belongs to")
	cobra.MarkFlagRequired(cmd.PersistentFlags(), "application")
	cmd.PersistentFlags().StringVarP(&options.name, "name", "n", "", "name of the pipeline")
	cobra.MarkFlagRequired(cmd.PersistentFlags(), "name")
	cmd.PersistentFlags().BoolVarP(&options.disabled, "disabled", "d", false, "enable or disable pipeline")
	cmd.PersistentFlags().BoolVarP(&options.enabled, "enabled", "e", false, "enable or disable pipeline")

	return cmd
}

func updatePipeline(cmd *cobra.Command, options *updateOptions) error {
	application := options.application
	pipelineName := options.name

	foundPipeline, queryResp, _ := options.GateClient.ApplicationControllerApi.GetPipelineConfigUsingGET(options.GateClient.Context, application, pipelineName)
	if queryResp.StatusCode == http.StatusNotFound {
		return fmt.Errorf("Pipeline %s not found under application %s", pipelineName, application)
	}

	if cmd.Flags().Changed("disabled") && cmd.Flags().Changed("enabled") {
		return fmt.Errorf("Cannot pass in both enabled and disabled flag")
	}

	if cmd.Flags().Changed("disabled") {
		// User passed in the disabled flag and so pipeline should update its value
		foundPipeline["disabled"] = options.disabled
	}

	if cmd.Flags().Changed("enabled") {
		// User passed in the enabled flag and so pipeline should update its value
		foundPipeline["disabled"] = !options.enabled
	}

	// TODO: support option passing in and remove nil in below call
	opt := &gate.PipelineControllerApiSavePipelineUsingPOSTOpts{}
	saveResp, err := options.GateClient.PipelineControllerApi.SavePipelineUsingPOST(options.GateClient.Context, foundPipeline, opt)
	if err != nil {
		return err
	}

	if saveResp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error saving pipeline, status code: %d\n", saveResp.StatusCode)
	}

	options.Ui.Success("Pipeline update succeeded")
	return nil
}
