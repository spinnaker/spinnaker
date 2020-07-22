package canary

import (
	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/cmd"
)

type CanaryOptions struct {
	*cmd.RootOptions
}

const (
	canaryShort   = ""
	canaryLong    = ""
	canaryExample = ""
)

func NewCanaryCmd(rootOptions *cmd.RootOptions) (*cobra.Command, *CanaryOptions) {
	options := &CanaryOptions{
		RootOptions: rootOptions,
	}
	cmd := &cobra.Command{
		Use:     "canary",
		Aliases: []string{},
		Short:   canaryShort,
		Long:    canaryLong,
		Example: canaryExample,
	}
	return cmd, options
}
