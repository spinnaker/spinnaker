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
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
)

type ListOptions struct {
	*pipelineTemplateOptions
	scopes *[]string
}

var (
	listPipelineTemplateShort = "List the pipeline templates for the provided scopes"
	listPipelineTemplateLong  = "List the pipeline templates for the provided scopes"
)

func NewListCmd(pipelineTemplateOptions pipelineTemplateOptions) *cobra.Command {
	options := ListOptions{
		pipelineTemplateOptions: &pipelineTemplateOptions,
	}
	cmd := &cobra.Command{
		Use:     "list",
		Aliases: []string{"ls"},
		Short:   listPipelineTemplateShort,
		Long:    listPipelineTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return listPipelineTemplate(cmd, options)
		},
	}

	// TODO(jacobkiefer): Document pipeline template scopes.
	options.scopes = cmd.PersistentFlags().StringArrayP("scopes", "", []string{}, "set of scopes to reduce the pipeline template list to")

	return cmd
}

func listPipelineTemplate(cmd *cobra.Command, options ListOptions) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	successPayload, resp, err := gateClient.V2PipelineTemplatesControllerApi.ListUsingGET1(gateClient.Context,
		map[string]interface{}{"scopes": options.scopes})

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error listing pipeline templates for scopes %v, status code: %d\n",
			options.scopes,
			resp.StatusCode)
	}

	util.UI.JsonOutput(successPayload, util.UI.OutputFormat)
	return nil
}
