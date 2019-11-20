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
	"fmt"
	"net/http"

	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"

	"github.com/spinnaker/spin/util"
)

type SaveOptions struct {
	*pipelineOptions
	output       string
	pipelineFile string
}

var (
	savePipelineShort = "Save the provided pipeline"
	savePipelineLong  = "Save the provided pipeline"
)

func NewSaveCmd(pipelineOptions pipelineOptions) *cobra.Command {
	options := SaveOptions{
		pipelineOptions: &pipelineOptions,
	}
	cmd := &cobra.Command{
		Use:     "save",
		Aliases: []string{},
		Short:   savePipelineShort,
		Long:    savePipelineLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return savePipeline(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.pipelineFile, "file", "f", "", "path to the pipeline file")

	return cmd
}

func savePipeline(cmd *cobra.Command, options SaveOptions) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	pipelineJson, err := util.ParseJsonFromFileOrStdin(options.pipelineFile, false)
	if err != nil {
		return err
	}
	valid := true
	if _, exists := pipelineJson["name"]; !exists {
		util.UI.Error("Required pipeline key 'name' missing...\n")
		valid = false
	}

	if _, exists := pipelineJson["application"]; !exists {
		util.UI.Error("Required pipeline key 'application' missing...\n")
		valid = false
	}

	if template, exists := pipelineJson["template"]; exists && len(template.(map[string]interface{})) > 0 {
		if _, exists := pipelineJson["schema"]; !exists {
			util.UI.Error("Required pipeline key 'schema' missing for templated pipeline...\n")
			valid = false
		}
	    pipelineJson["type"] = "templatedPipeline"
	}

	if !valid {
		return fmt.Errorf("Submitted pipeline is invalid: %s\n", pipelineJson)
	}
	application := pipelineJson["application"].(string)
	pipelineName := pipelineJson["name"].(string)

	foundPipeline, queryResp, _ := gateClient.ApplicationControllerApi.GetPipelineConfigUsingGET(gateClient.Context, application, pipelineName)

	if queryResp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error querying pipeline, status code: %d\n", queryResp.StatusCode)
	}

	_, exists := pipelineJson["id"].(string)
	var foundPipelineId string
	if len(foundPipeline) > 0 {
		foundPipelineId = foundPipeline["id"].(string)
	}
	if !exists && foundPipelineId != "" {
		pipelineJson["id"] = foundPipelineId
	}

	saveResp, saveErr := gateClient.PipelineControllerApi.SavePipelineUsingPOST(gateClient.Context, pipelineJson)

	if saveErr != nil {
		return saveErr
	}
	if saveResp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error saving pipeline, status code: %d\n", saveResp.StatusCode)
	}

	util.UI.Info(util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Pipeline save succeeded")))
	return nil
}
