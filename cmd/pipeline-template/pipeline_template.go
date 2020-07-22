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
	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/cmd"
)

type pipelineTemplateOptions struct {
	*cmd.RootOptions
}

var (
	pipelineTemplateShort   = ""
	pipelineTemplateLong    = ""
	pipelineTemplateExample = ""
)

func NewPipelineTemplateCmd(rootOptions *cmd.RootOptions) *cobra.Command {
	options := &pipelineTemplateOptions{
		RootOptions: rootOptions,
	}
	cmd := &cobra.Command{
		Use:     "pipeline-template",
		Aliases: []string{"pipeline-templates", "pt"},
		Short:   pipelineTemplateShort,
		Long:    pipelineTemplateLong,
		Example: pipelineTemplateExample,
	}

	// create subcommands
	cmd.AddCommand(NewGetCmd(options))
	cmd.AddCommand(NewListCmd(options))
	cmd.AddCommand(NewSaveCmd(options))
	cmd.AddCommand(NewDeleteCmd(options))
	cmd.AddCommand(NewPlanCmd(options))
	cmd.AddCommand(NewUseCmd(options))

	return cmd
}
