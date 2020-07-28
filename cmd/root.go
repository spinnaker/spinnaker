package cmd

import (
	"io"

	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/cmd/output"
	"github.com/spinnaker/spin/version"
)

type RootOptions struct {
	configPath       string
	gateEndpoint     string
	ignoreCertErrors bool
	quiet            bool
	color            bool
	outputFormat     string
	defaultHeaders   string

	Ui         output.Ui
	GateClient *gateclient.GatewayClient
}

// TODO(karlkfi): Pipe stdin reader through NewCmdRoot for testing
func NewCmdRoot(outWriter, errWriter io.Writer) (*cobra.Command, *RootOptions) {
	options := &RootOptions{}

	cmd := &cobra.Command{
		SilenceUsage:  true,
		SilenceErrors: true,
		Version:       version.String(),
	}

	cmd.SetOut(outWriter)
	cmd.SetErr(errWriter)

	// GateClient Flags
	cmd.PersistentFlags().StringVar(&options.configPath, "config", "", "path to config file (default $HOME/.spin/config)")
	cmd.PersistentFlags().StringVar(&options.gateEndpoint, "gate-endpoint", "", "Gate (API server) endpoint (default http://localhost:8084)")
	cmd.PersistentFlags().BoolVarP(&options.ignoreCertErrors, "insecure", "k", false, "ignore certificate errors")
	cmd.PersistentFlags().StringVar(&options.defaultHeaders, "default-headers", "", "configure default headers for gate client as comma separated list (e.g. key1=value1,key2=value2)")

	// UI Flags
	cmd.PersistentFlags().BoolVarP(&options.quiet, "quiet", "q", false, "squelch non-essential output")
	cmd.PersistentFlags().BoolVar(&options.color, "no-color", true, "disable color")
	cmd.PersistentFlags().StringVarP(&options.outputFormat, "output", "o", "", "configure output formatting")

	// Initialize UI & GateClient
	outw := outWriter
	errw := errWriter
	cmd.PersistentPreRunE = func(cmd *cobra.Command, args []string) error {
		outputFormater, err := output.ParseOutputFormat(options.outputFormat)
		if err != nil {
			return err
		}
		options.Ui = output.NewUI(options.quiet, options.color, outputFormater, outw, errw)

		gateClient, err := gateclient.NewGateClient(
			options.Ui,
			options.gateEndpoint,
			options.defaultHeaders,
			options.configPath,
			options.ignoreCertErrors,
		)
		if err != nil {
			return err
		}
		options.GateClient = gateClient

		return nil
	}

	return cmd, options
}
