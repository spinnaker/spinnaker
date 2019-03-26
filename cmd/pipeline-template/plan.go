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
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
)

type PlanOptions struct {
	*pipelineTemplateOptions
	configPath string
}

const (
	planPipelineTemplateShort = "Plan the provided pipeline template config"
	planPipelineTemplateLong  = "Plan the provided pipeline template config"
)

func NewPlanCmd(pipelineTemplateOptions pipelineTemplateOptions) *cobra.Command {
	options := PlanOptions{
		pipelineTemplateOptions: &pipelineTemplateOptions,
	}
	cmd := &cobra.Command{
		Use:   "plan",
		Short: planPipelineTemplateShort,
		Long:  planPipelineTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return planPipelineTemplate(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.configPath, "file", "f", "", "path to the pipeline template config file")

	return cmd
}

func planPipelineTemplate(cmd *cobra.Command, options PlanOptions) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	configJson, err := util.ParseJsonFromFileOrStdin(options.configPath, false)
	if err != nil {
		return err
	}

	if _, exists := configJson["schema"]; !exists {
		return errors.New("Required pipeline key 'schema' missing for templated pipeline config...\n")
	}

	successPayload, resp, err := gateClient.V2PipelineTemplatesControllerApi.PlanUsingPOST(gateClient.Context, configJson)

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error planning pipeline template config, status code: %d\n",
			resp.StatusCode)
	}

	util.UI.JsonOutput(successPayload, util.UI.OutputFormat)
	return nil
}
