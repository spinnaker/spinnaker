package command

import (
	"flag"
	"strings"
)

// Meta is the state & utility shared by our command parser.
type ApiMeta struct {
	// This is the set of flags global to the command parser.
	gateEndpoint string
}

// This adds all global options to the flagset, and returns the flagset object
// for further modification by the subcommand.
func (m *ApiMeta) GlobalFlagSet(cmd string) *flag.FlagSet {
	f := flag.NewFlagSet(cmd, flag.ContinueOnError)

	f.StringVar(&m.gateEndpoint, "gate-endpoint", "http://localhost:8084", "Gate (API server) endpoint")

	f.Usage = func() {}

	return f
}

func (m *ApiMeta) Help() string {
	help := `
Global Options:

	--gate-endpoint         Gate (API server) endpoint.
	`

	return strings.TrimSpace(help)
}
