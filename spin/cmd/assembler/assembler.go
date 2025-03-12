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

package assembler

import (
	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/account"
	"github.com/spinnaker/spin/cmd/application"
	"github.com/spinnaker/spin/cmd/canary"
	canary_config "github.com/spinnaker/spin/cmd/canary/canary-config"
	"github.com/spinnaker/spin/cmd/pipeline"
	pipeline_template "github.com/spinnaker/spin/cmd/pipeline-template"
	"github.com/spinnaker/spin/cmd/pipeline/execution"
	"github.com/spinnaker/spin/cmd/project"
)

// AddSubCommands adds all the subcommands to the rootCmd.
// rootOpts are passed through to the subcommands.
func AddSubCommands(rootCmd *cobra.Command, rootOpts *cmd.RootOptions) {
	rootCmd.AddCommand(account.NewAccountCmd(rootOpts))

	rootCmd.AddCommand(application.NewApplicationCmd(rootOpts))

	canaryCmd, canaryOpts := canary.NewCanaryCmd(rootOpts)
	canaryCmd.AddCommand(canary_config.NewCanaryConfigCmd(canaryOpts))
	rootCmd.AddCommand(canaryCmd)

	pipelineCmd, pipelineOpts := pipeline.NewPipelineCmd(rootOpts)
	pipelineCmd.AddCommand(execution.NewExecutionCmd(pipelineOpts))
	rootCmd.AddCommand(pipelineCmd)

	rootCmd.AddCommand(pipeline_template.NewPipelineTemplateCmd(rootOpts))

	rootCmd.AddCommand(project.NewProjectCmd(rootOpts))
}
