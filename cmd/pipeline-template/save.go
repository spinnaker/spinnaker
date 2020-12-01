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

package pipeline_template

import (
	"fmt"
	"net/http"

	"github.com/antihax/optional"
	"github.com/spf13/cobra"

	orca_tasks "github.com/spinnaker/spin/cmd/orca-tasks"
	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

type saveOptions struct {
	*pipelineTemplateOptions
	output       string
	templateFile string
	tag          string
}

var (
	saveTemplateShort = "Save the provided pipeline template"
	saveTemplateLong  = "Save the provided pipeline template"
)

func NewSaveCmd(pipelineTemplateOptions *pipelineTemplateOptions) *cobra.Command {
	options := &saveOptions{
		pipelineTemplateOptions: pipelineTemplateOptions,
	}
	cmd := &cobra.Command{
		Use:     "save",
		Aliases: []string{},
		Short:   saveTemplateShort,
		Long:    saveTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return savePipelineTemplate(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.templateFile, "file",
		"f", "", "path to the pipeline template file")
	cmd.PersistentFlags().StringVar(&options.tag, "tag", "",
		"(optional) specific tag to tag pipeline template with")

	return cmd
}

func savePipelineTemplate(cmd *cobra.Command, options *saveOptions) error {
	templateJson, err := util.ParseJsonFromFileOrStdin(options.templateFile, false)
	if err != nil {
		return err
	}

	valid := true
	if _, exists := templateJson["id"]; !exists {
		options.Ui.Error("Required pipeline template key 'id' missing...\n")
		valid = false
	}
	if _, exists := templateJson["schema"]; !exists {
		options.Ui.Error("Required pipeline template key 'schema' missing...\n")
		valid = false
	}
	if !valid {
		return fmt.Errorf("Submitted pipeline is invalid: %s\n", templateJson)
	}

	templateId := templateJson["id"].(string)

	getQueryParam := &gate.V2PipelineTemplatesControllerApiGetUsingGET2Opts{}
	if options.tag != "" {
		getQueryParam.Tag = optional.NewString(options.tag)
	}

	_, resp, queryErr := options.GateClient.V2PipelineTemplatesControllerApi.GetUsingGET2(options.GateClient.Context, templateId, getQueryParam)

	var saveResp *http.Response
	var saveRet map[string]interface{}
	var saveErr error

	switch resp.StatusCode {
	case http.StatusOK:
		opt := &gate.V2PipelineTemplatesControllerApiUpdateUsingPOST1Opts{}
		if options.tag != "" {
			opt.Tag = optional.NewString(options.tag)
		}

		saveRet, saveResp, saveErr = options.GateClient.V2PipelineTemplatesControllerApi.UpdateUsingPOST1(options.GateClient.Context, templateId, templateJson, opt)
	case http.StatusNotFound:
		opt := &gate.V2PipelineTemplatesControllerApiCreateUsingPOST1Opts{}
		if options.tag != "" {
			opt.Tag = optional.NewString(options.tag)
		}

		saveRet, saveResp, saveErr = options.GateClient.V2PipelineTemplatesControllerApi.CreateUsingPOST1(options.GateClient.Context, templateJson, opt)
	default:
		if queryErr != nil {
			return queryErr
		}
		return fmt.Errorf("Encountered an unexpected status code %d querying pipeline template with id %s\n",
			resp.StatusCode, templateId)
	}

	if saveErr != nil {
		return saveErr
	}

	if saveResp.StatusCode != http.StatusAccepted && saveResp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error saving pipeline template %v, status code: %d\n",
			templateJson,
			saveResp.StatusCode)
	}

	if len(saveRet) > 0 {
		taskSucceeded := orca_tasks.TaskSucceeded(saveRet)
		if !taskSucceeded {
			return fmt.Errorf("Encountered an error with saving pipeline template %v", saveRet)
		}
	}

	options.Ui.Success("Pipeline template save succeeded")
	return nil
}
