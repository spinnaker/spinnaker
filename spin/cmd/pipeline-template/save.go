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

	"github.com/spf13/cobra"

	orca_tasks "github.com/spinnaker/spin/cmd/orca-tasks"
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

	getReq := options.GateClient.V2PipelineTemplatesControllerAPI.Get1(options.GateClient.Context, templateId)
	if options.tag != "" {
		getReq = getReq.Tag(options.tag)
	} else if tag, exists := templateJson["tag"]; exists {
		// Use comma-ok assertion to avoid panic if tag is non-string (e.g. JSON array).
		if tagStr, ok := tag.(string); ok && tagStr != "" {
			getReq = getReq.Tag(tagStr)
		} else {
			return fmt.Errorf(
				"Pipeline template tag must be a string (valid values: latest, stable, unstable, experimental, test, canary), got: %v",
				tag,
			)
		}
	}

	_, resp, queryErr := getReq.Execute()

	var saveResp *http.Response
	var saveRet map[string]interface{}
	var saveErr error

	switch resp.StatusCode {
	case http.StatusOK:
		updateReq := options.GateClient.V2PipelineTemplatesControllerAPI.Update(options.GateClient.Context, templateId).RequestBody(templateJson)
		if options.tag != "" {
			updateReq = updateReq.Tag(options.tag)
		}
		saveRet, saveResp, saveErr = updateReq.Execute()
	case http.StatusNotFound:
		createReq := options.GateClient.V2PipelineTemplatesControllerAPI.Create(options.GateClient.Context).RequestBody(templateJson)
		if options.tag != "" {
			createReq = createReq.Tag(options.tag)
		}
		saveRet, saveResp, saveErr = createReq.Execute()
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
