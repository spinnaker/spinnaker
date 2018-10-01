package cmd

import (
	"io"

	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/application"
	"github.com/spinnaker/spin/cmd/pipeline"
)

type RootOptions struct {
	configFile       string
	GateEndpoint     string
	ignoreCertErrors bool
	quiet            bool
	color            bool
	outputFormat     string
}

func Execute(out io.Writer) error {
	cmd := NewCmdRoot(out)
	return cmd.Execute()
}

func NewCmdRoot(out io.Writer) *cobra.Command {
	options := RootOptions{}

	cmd := &cobra.Command{
		Short: `Global Options:

		--gate-endpoint               Gate (API server) endpoint.
		--no-color                    Removes color from CLI output.
		--insecure=false              Ignore certificate errors during connection to endpoints.
		--quiet=false                 Squelch non-essential output.
		--output <output format>      Formats CLI output.
	`,
		SilenceUsage: true,
	}

	cmd.PersistentFlags().StringVar(&options.configFile, "config", "", "config file (default is $HOME/.spin/config)")
	cmd.PersistentFlags().StringVar(&options.GateEndpoint, "gate-endpoint", "", "Gate (API server) endpoint. Default http://localhost:8084")
	cmd.PersistentFlags().BoolVar(&options.ignoreCertErrors, "insecure", false, "Ignore Certificate Errors")
	cmd.PersistentFlags().BoolVar(&options.quiet, "quiet", false, "Squelch non-essential output")
	cmd.PersistentFlags().BoolVar(&options.color, "no-color", true, "Disable color")
	// TODO(jacobkiefer): Codify the json-path as part of an OutputConfig or
	// something similar. Sets the stage for yaml output, etc.
	cmd.PersistentFlags().StringVar(&options.outputFormat, "output", "", "Configure output formatting")

	// create subcommands
	cmd.AddCommand(application.NewApplicationCmd(out))
	cmd.AddCommand(pipeline.NewPipelineCmd(out))

	return cmd
}
