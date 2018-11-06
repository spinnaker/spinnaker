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
		Aliases: []string{"pipelines", "pi"},
		Short:   pipelineShort,
		Long:    pipelineLong,
		Example: pipelineExample,
	}

	// create subcommands
	cmd.AddCommand(NewGetCmd(options))
	cmd.AddCommand(NewListCmd(options))
	cmd.AddCommand(NewDeleteCmd(options))
	cmd.AddCommand(NewSaveCmd(options))
	cmd.AddCommand(NewExecuteCmd(options))
	return cmd
}
