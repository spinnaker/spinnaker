package project

import (
	"io"

	"github.com/spf13/cobra"
)

type projectOptions struct {
}

var (
	projectShort   = ""
	projectLong    = ""
	projectExample = ""
)

func NewProjectCmd(out io.Writer) *cobra.Command {
	options := projectOptions{}
	cmd := &cobra.Command{
		Use:     "project",
		Aliases: []string{"project", "prj"},
		Short:   projectShort,
		Long:    projectLong,
		Example: projectExample,
	}

	// create subcommands
	cmd.AddCommand(NewGetCmd(options))
	return cmd
}
