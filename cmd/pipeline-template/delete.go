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
	"github.com/spinnaker/spin/util"
)

type deleteOptions struct {
	*pipelineTemplateOptions
	tag string
}

var (
	deletePipelineTemplateShort = "Delete the provided pipeline template"
	deletePipelineTemplateLong  = "Delete the provided pipeline template"
)

func NewDeleteCmd(pipelineTemplateOptions *pipelineTemplateOptions) *cobra.Command {
	options := &deleteOptions{
		pipelineTemplateOptions: pipelineTemplateOptions,
	}

	cmd := &cobra.Command{
		Use:     "delete",
		Aliases: []string{"del"},
		Short:   deletePipelineTemplateShort,
		Long:    deletePipelineTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return deletePipelineTemplate(cmd, options, args)
		},
	}

	cmd.PersistentFlags().StringVar(&options.tag, "tag", "",
		"(optional) specific tag to query")

	return cmd
}

func deletePipelineTemplate(cmd *cobra.Command, options *deleteOptions, args []string) error {
	id, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	queryParams := &gate.V2PipelineTemplatesControllerApiDeleteUsingDELETE1Opts{}
	if options.tag != "" {
		queryParams.Tag = optional.NewString(options.tag)
	}

	_, resp, err := options.GateClient.V2PipelineTemplatesControllerApi.DeleteUsingDELETE1(options.GateClient.Context, id, queryParams)
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusAccepted {
		return fmt.Errorf("Encountered an error deleting pipeline template, status code: %d\n", resp.StatusCode)
	}

	options.Ui.Success(fmt.Sprintf("Pipeline template %s deleted", id))
	return nil
}
