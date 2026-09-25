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
)

type updateOptions struct {
	*PipelineOptions
	disabled    bool
	enabled     bool
	application string
	name        string
	staleCheck  bool
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
	cmd.PersistentFlags().BoolVar(&options.staleCheck, "stale-check", true,
		"fail instead of overwriting if the pipeline changed in Spinnaker after this command read it")

	return cmd
}

func updatePipeline(cmd *cobra.Command, options *updateOptions) error {
	application := options.application
	pipelineName := options.name

	foundPipeline, queryResp, queryErr := options.GateClient.ApplicationControllerAPI.
		GetPipelineConfig(options.GateClient.Context, application, pipelineName).Execute()
	if queryResp == nil {
		return fmt.Errorf("failed to look up pipeline %q in application %q: %w", pipelineName, application, queryErr)
	}
	defer queryResp.Body.Close()
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

	// foundPipeline is the map we just fetched, so it already carries the server's
	// updateTs fingerprint through to the save.
	saveReq := options.GateClient.PipelineControllerAPI.SavePipeline(options.GateClient.Context).RequestBody(foundPipeline)
	if options.staleCheck {
		saveReq = saveReq.StaleCheck(true)
	}

	saveResp, err := saveReq.Execute()
	if err != nil {
		return savePipelineError(application, pipelineName, saveResp, err)
	}
	if saveResp.StatusCode != http.StatusOK {
		return savePipelineError(application, pipelineName, saveResp, nil)
	}

	options.Ui.Success("Pipeline update succeeded")
	return nil
}
