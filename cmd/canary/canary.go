package canary

import (
	"github.com/spf13/cobra"
	canary_config "github.com/spinnaker/spin/cmd/canary/canary-config"
	"io"
)

type canaryOptions struct{}

const (
	canaryShort   = ""
	canaryLong    = ""
	canaryExample = ""
)

func NewCanaryCmd(out io.Writer) *cobra.Command {
	cmd := &cobra.Command{
		Use:     "canary",
		Aliases: []string{},
		Short:   canaryShort,
		Long:    canaryLong,
		Example: canaryExample,
	}

	// create subcommands
	cmd.AddCommand(canary_config.NewCanaryConfigCmd(out))
	return cmd
}