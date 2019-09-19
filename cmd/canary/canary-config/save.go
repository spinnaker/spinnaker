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
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
)

type SaveOptions struct {
	*canaryConfigOptions
	output       string
	templateFile string
}

const (
	saveTemplateShort = "Save the provided canary config"
	saveTemplateLong  = "Save the provided canary config"
)

func NewSaveCmd(canaryConfigOptions canaryConfigOptions) *cobra.Command {
	options := SaveOptions{
		canaryConfigOptions: &canaryConfigOptions,
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

func saveCanaryConfig(cmd *cobra.Command, options SaveOptions) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	templateJson, err := util.ParseJsonFromFileOrStdin(options.templateFile, false)
	if err != nil {
		return err
	}

	if _, exists := templateJson["id"]; !exists {
		util.UI.Error("Required canary config key 'id' missing...\n")
		return fmt.Errorf("Submitted canary config is invalid: %s\n", templateJson)
	}

	templateId := templateJson["id"].(string)

	_, resp, queryErr := gateClient.V2CanaryConfigControllerApi.GetCanaryConfigUsingGET(
		gateClient.Context, templateId, map[string]interface{}{})

	var saveResp *http.Response
	var saveErr error
	if resp.StatusCode == http.StatusOK {
		_, saveResp, saveErr = gateClient.V2CanaryConfigControllerApi.UpdateCanaryConfigUsingPUT(
			gateClient.Context, templateJson, templateId, map[string]interface{}{})
	} else if resp.StatusCode == http.StatusNotFound {
		_, saveResp, saveErr = gateClient.V2CanaryConfigControllerApi.CreateCanaryConfigUsingPOST(
			gateClient.Context, templateJson, map[string]interface{}{})
	} else {
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

	util.UI.Info(util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Canary config save succeeded")))
	return nil
}
