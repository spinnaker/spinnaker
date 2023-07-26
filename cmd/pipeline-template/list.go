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

	gate "github.com/spinnaker/spin/gateapi"
)

type listOptions struct {
	*pipelineTemplateOptions
	scopes *[]string
}

var (
	listPipelineTemplateShort = "List the pipeline templates for the provided scopes"
	listPipelineTemplateLong  = "List the pipeline templates for the provided scopes"
)

func NewListCmd(pipelineTemplateOptions *pipelineTemplateOptions) *cobra.Command {
	options := &listOptions{
		pipelineTemplateOptions: pipelineTemplateOptions,
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

func listPipelineTemplate(cmd *cobra.Command, options *listOptions) error {
	successPayload, resp, err := options.GateClient.V2PipelineTemplatesControllerApi.ListUsingGET1(options.GateClient.Context,
		&gate.V2PipelineTemplatesControllerApiListUsingGET1Opts{Scopes: optional.NewInterface(*options.scopes)})
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error listing pipeline templates for scopes %v, status code: %d\n",
			options.scopes,
			resp.StatusCode)
	}

	options.Ui.JsonOutput(successPayload)
	return nil
}
