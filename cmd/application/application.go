package application

import (
	"io"

	"github.com/spf13/cobra"
)

type applicationOptions struct {
}

var (
	applicationShort   = ""
	applicationLong    = ""
	applicationExample = ""
)

func NewApplicationCmd(out io.Writer) *cobra.Command {
	options := applicationOptions{}
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
	return cmd
}
