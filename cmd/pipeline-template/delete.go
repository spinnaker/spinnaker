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
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
)

type DeleteOptions struct {
	*pipelineTemplateOptions
	tag string
}

var (
	deletePipelineTemplateShort   = "Delete the provided pipeline template"
	deletePipelineTemplateLong    = "Delete the provided pipeline template"
)

func NewDeleteCmd(pipelineTemplateOptions pipelineTemplateOptions) *cobra.Command {
	options := DeleteOptions{
		pipelineTemplateOptions: &pipelineTemplateOptions,
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

func deletePipelineTemplate(cmd *cobra.Command, options DeleteOptions, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	id, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	queryParams := map[string]interface{}{}
	if options.tag != "" {
		queryParams["tag"] = options.tag
	}

	_, resp, err := gateClient.V2PipelineTemplatesControllerApi.DeleteUsingDELETE1(gateClient.Context, id, queryParams)

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusAccepted {
		return fmt.Errorf("Encountered an error deleting pipeline template, status code: %d\n", resp.StatusCode)
	}

	util.UI.Info(util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Pipeline template %s deleted", id)))
	return nil
}
