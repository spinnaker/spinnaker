// Copyright (c) 2020, Anosua "Chini" Mukhopadhyay
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

package project

import (
	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/cmd"
)

type projectOptions struct {
	*cmd.RootOptions
}

const (
	projectShort   = ""
	projectLong    = ""
	projectExample = ""
)

func NewProjectCmd(rootOptions *cmd.RootOptions) *cobra.Command {
	options := &projectOptions{
		RootOptions: rootOptions,
	}
	cmd := &cobra.Command{
		Use:     "project",
		Aliases: []string{"prj"},
		Short:   projectShort,
		Long:    projectLong,
		Example: projectExample,
	}

	// create subcommands
	cmd.AddCommand(NewListCmd(options))
	cmd.AddCommand(NewGetCmd(options))
	cmd.AddCommand(NewGetPipelinesCmd(options))
	cmd.AddCommand(NewSaveCmd(options))
	cmd.AddCommand(NewDeleteCmd(options))
	return cmd
}
