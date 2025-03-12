// Copyright (c) 2019, Waze, Inc.
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

package canary_config

import (
	"fmt"
	"net/http"

	gate "github.com/spinnaker/spin/gateapi"

	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/util"
)

type saveOptions struct {
	*canaryConfigOptions
	output       string
	templateFile string
}

const (
	saveTemplateShort = "Save the provided canary config"
	saveTemplateLong  = "Save the provided canary config"
)

func NewSaveCmd(canaryConfigOptions *canaryConfigOptions) *cobra.Command {
	options := &saveOptions{
		canaryConfigOptions: canaryConfigOptions,
	}
	cmd := &cobra.Command{
		Use:     "save",
		Aliases: []string{},
		Short:   saveTemplateShort,
		Long:    saveTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return saveCanaryConfig(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.templateFile, "file",
		"f", "", "path to the canary config file")

	return cmd
}

func saveCanaryConfig(cmd *cobra.Command, options *saveOptions) error {
	templateJson, err := util.ParseJsonFromFileOrStdin(options.templateFile, false)
	if err != nil {
		return err
	}

	if _, exists := templateJson["id"]; !exists {
		options.Ui.Error("Required canary config key 'id' missing...\n")
		return fmt.Errorf("Submitted canary config is invalid: %s\n", templateJson)
	}

	templateId := templateJson["id"].(string)

	_, resp, queryErr := options.GateClient.V2CanaryConfigControllerApi.GetCanaryConfigUsingGET(
		options.GateClient.Context, templateId, &gate.V2CanaryConfigControllerApiGetCanaryConfigUsingGETOpts{})

	var saveResp *http.Response
	var saveErr error
	switch resp.StatusCode {
	case http.StatusOK:
		_, saveResp, saveErr = options.GateClient.V2CanaryConfigControllerApi.UpdateCanaryConfigUsingPUT(
			options.GateClient.Context, templateJson, templateId, &gate.V2CanaryConfigControllerApiUpdateCanaryConfigUsingPUTOpts{})
	case http.StatusNotFound:
		_, saveResp, saveErr = options.GateClient.V2CanaryConfigControllerApi.CreateCanaryConfigUsingPOST(
			options.GateClient.Context, templateJson, &gate.V2CanaryConfigControllerApiCreateCanaryConfigUsingPOSTOpts{})
	default:
		if queryErr != nil {
			return queryErr
		}
		return fmt.Errorf(
			"Encountered an unexpected status code %d querying canary config with id %s\n",
			resp.StatusCode, templateId)
	}

	if saveErr != nil {
		return saveErr
	}

	if saveResp.StatusCode != http.StatusOK {
		return fmt.Errorf(
			"Encountered an error saving canary config %v, status code: %d\n",
			templateJson, saveResp.StatusCode)
	}

	options.Ui.Success("Canary config save succeeded")
	return nil
}
