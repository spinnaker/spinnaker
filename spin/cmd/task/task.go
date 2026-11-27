// Copyright (c) 2026, Spinnaker Authors.
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

package task

import (
	"github.com/spf13/cobra"

	"github.com/spinnaker/spinnaker/spin/cmd"
)

type taskOptions struct {
	*cmd.RootOptions
}

var (
	taskShort   = "Submit, inspect and cancel ad-hoc tasks (orchestrations)"
	taskLong    = "Submit, inspect and cancel ad-hoc tasks, the orchestrations Spinnaker runs outside of a pipeline (for example resizing a server group or deleting a load balancer)"
	taskExample = ""
)

func NewTaskCmd(rootOptions *cmd.RootOptions) *cobra.Command {
	options := &taskOptions{
		RootOptions: rootOptions,
	}
	cmd := &cobra.Command{
		Use:     "task",
		Aliases: []string{"tasks"},
		Short:   taskShort,
		Long:    taskLong,
		Example: taskExample,
	}

	// create subcommands
	cmd.AddCommand(NewCancelCmd(options))
	cmd.AddCommand(NewGetCmd(options))
	cmd.AddCommand(NewSubmitCmd(options))
	return cmd
}
