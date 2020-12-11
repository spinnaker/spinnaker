package application

import (
	"github.com/spf13/cobra"
)

type pipelineOptions struct {
	*applicationOptions
}

var (
	pipelinesShort   = ""
	pipelinesLong    = ""
	pipelinesExample = ""
)

func NewPipelineCmd(appOptions *applicationOptions) *cobra.Command {
	options := &pipelineOptions{
		applicationOptions: appOptions,
	}
	cmd := &cobra.Command{
		Use:     "pipelines",
		Aliases: []string{"pipelines", "pipes"},
		Short:   pipelinesShort,
		Long:    pipelinesLong,
		Example: pipelinesExample,
	}

	// create subcommands
	cmd.AddCommand(NewGetPipelinesCmd(options))
	cmd.AddCommand(NewCancelPipelineCmd(options))
	cmd.AddCommand(NewCancelAllPipelinesCmd(options))
	return cmd
}
