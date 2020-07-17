package project

import (
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd"
)

type projectOptions struct {
	*cmd.RootOptions
}

var (
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
		Aliases: []string{"project", "prj"},
		Short:   projectShort,
		Long:    projectLong,
		Example: projectExample,
	}

	// create subcommands
	cmd.AddCommand(NewGetPipelinesCmd(options))
	return cmd
}
