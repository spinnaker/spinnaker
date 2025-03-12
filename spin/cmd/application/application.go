package application

import (
	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/cmd"
)

type applicationOptions struct {
	*cmd.RootOptions
}

var (
	applicationShort   = ""
	applicationLong    = ""
	applicationExample = ""
)

func NewApplicationCmd(rootOptions *cmd.RootOptions) *cobra.Command {
	options := &applicationOptions{
		RootOptions: rootOptions,
	}
	cmd := &cobra.Command{
		Use:     "application",
		Aliases: []string{"applications", "app"},
		Short:   applicationShort,
		Long:    applicationLong,
		Example: applicationExample,
	}

	// create subcommands
	cmd.AddCommand(NewGetCmd(options))
	cmd.AddCommand(NewListCmd(options))
	cmd.AddCommand(NewDeleteCmd(options))
	cmd.AddCommand(NewSaveCmd(options))
	cmd.AddCommand(NewPipelineCmd(options))
	return cmd
}
