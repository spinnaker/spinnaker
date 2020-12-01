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
	"io/ioutil"
	"net/http"

	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

type saveOptions struct {
	*PipelineOptions
	output       string
	pipelineFile string
}

var (
	savePipelineShort = "Save the provided pipeline"
	savePipelineLong  = "Save the provided pipeline"
)

func NewSaveCmd(pipelineOptions *PipelineOptions) *cobra.Command {
	options := &saveOptions{
		PipelineOptions: pipelineOptions,
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

func savePipeline(cmd *cobra.Command, options *saveOptions) error {
	pipelineJson, err := util.ParseJsonFromFileOrStdin(options.pipelineFile, false)
	if err != nil {
		return err
	}
	valid := true
	if _, exists := pipelineJson["name"]; !exists {
		options.Ui.Error("Required pipeline key 'name' missing...\n")
		valid = false
	}

	if _, exists := pipelineJson["application"]; !exists {
		options.Ui.Error("Required pipeline key 'application' missing...\n")
		valid = false
	}

	if template, exists := pipelineJson["template"]; exists && len(template.(map[string]interface{})) > 0 {
		if _, exists := pipelineJson["schema"]; !exists {
			options.Ui.Error("Required pipeline key 'schema' missing for templated pipeline...\n")
			valid = false
		}
		pipelineJson["type"] = "templatedPipeline"
	}

	if !valid {
		return fmt.Errorf("Submitted pipeline is invalid: %s\n", pipelineJson)
	}

	application := pipelineJson["application"].(string)
	pipelineName := pipelineJson["name"].(string)

	foundPipeline, queryResp, _ := options.GateClient.ApplicationControllerApi.GetPipelineConfigUsingGET(options.GateClient.Context, application, pipelineName)
	switch queryResp.StatusCode {
	case http.StatusOK:
		// pipeline found, let's use Spinnaker's known Pipeline ID, otherwise we'll get one created for us
		if len(foundPipeline) > 0 {
			pipelineJson["id"] = foundPipeline["id"].(string)
		}
	case http.StatusNotFound:
		// pipeline doesn't exists, let's create a new one
	default:
		b, _ := ioutil.ReadAll(queryResp.Body)
		return fmt.Errorf("unhandled response %d: %s", queryResp.StatusCode, b)
	}

	// TODO: support option passing in and remove nil in below call
	opt := &gate.PipelineControllerApiSavePipelineUsingPOSTOpts{}
	saveResp, err := options.GateClient.PipelineControllerApi.SavePipelineUsingPOST(options.GateClient.Context, pipelineJson, opt)
	if err != nil {
		return err
	}
	if saveResp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error saving pipeline, status code: %d\n", saveResp.StatusCode)
	}

	options.Ui.Success("Pipeline save succeeded")
	return nil
}
