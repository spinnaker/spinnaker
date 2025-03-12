package pipeline

import (
	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/cmd"
)

type PipelineOptions struct {
	*cmd.RootOptions
}

var (
	pipelineShort   = ""
	pipelineLong    = ""
	pipelineExample = ""
)

func NewPipelineCmd(rootOptions *cmd.RootOptions) (*cobra.Command, *PipelineOptions) {
	options := &PipelineOptions{
		RootOptions: rootOptions,
	}
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
	cmd.AddCommand(NewUpdateCmd(options))
	return cmd, options
}
