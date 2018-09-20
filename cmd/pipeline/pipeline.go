package pipeline

import (
	"github.com/spf13/cobra"
	"io"
)

type pipelineOptions struct{}

var (
	pipelineShort   = ""
	pipelineLong    = ""
	pipelineExample = ""
)

func NewPipelineCmd(out io.Writer) *cobra.Command {
	options := pipelineOptions{}
	cmd := &cobra.Command{
		Use:     "pipeline",
		Aliases: []string{"pipelines"},
		Short:   pipelineShort,
		Long:    pipelineLong,
		Example: pipelineExample,
		Run: func(cmd *cobra.Command, args []string) {

		},
	}

	// create subcommands
	cmd.AddCommand(NewGetCmd(options))
	cmd.AddCommand(NewListCmd(options))
	cmd.AddCommand(NewDeleteCmd(options))
	cmd.AddCommand(NewSaveCmd(options))
	cmd.AddCommand(NewExecuteCmd(options))
	return cmd
}
