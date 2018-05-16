package command

import (
	"flag"
	"fmt"
	"io/ioutil"
	"os"
	"os/user"
	"path/filepath"
	"strings"

	"github.com/mitchellh/cli"
	"github.com/mitchellh/colorstring"
	"github.com/spinnaker/spin/config"
	gate "github.com/spinnaker/spin/gateapi"
	"gopkg.in/yaml.v2"
)

// ApiMeta is the state & utility shared by our commands.
type ApiMeta struct {
	// The exported fields below should be set by anyone using a command
	// with an ApiMeta field. These are expected to be set externally
	// (not from within the command itself).

	Color bool   // True if output should be colored
	Ui    cli.Ui // Ui for output

	// Gate Api client.
	GateClient *gate.APIClient

	// Spin CLI configuration.
	Config config.Config

	// Internal fields
	color bool

	// This is the set of flags global to the command parser.
	gateEndpoint string
}

// GlobalFlagSet adds all global options to the flagset, and returns the flagset object
// for further modification by the subcommand.
func (m *ApiMeta) GlobalFlagSet(cmd string) *flag.FlagSet {
	f := flag.NewFlagSet(cmd, flag.ContinueOnError)

	f.StringVar(&m.gateEndpoint, "gate-endpoint", "http://localhost:8084",
		"Gate (API server) endpoint")

	f.Usage = func() {}

	return f
}

// Process will process the meta-parameters out of the arguments. This
// potentially modifies the args in-place. It will return the resulting slice.
// NOTE: This expects the flag set to be parsed prior to invoking it.
func (m *ApiMeta) Process(args []string) ([]string, error) {
	// Do the Ui initialization so we can properly warn if Process() fails.
	// Colorization.
	m.Color = true
	m.color = m.Color
	for i, v := range args {
		if v == "--no-color" {
			m.color = false
			m.Color = false
			args = append(args[:i], args[i+1:]...)
			break
		}
	}

	// Set the Ui.
	m.Ui = &ColorizeUi{
		Colorize:   m.Colorize(),
		ErrorColor: "[red]",
		WarnColor:  "[yellow]",
		InfoColor:  "[blue]",
		Ui:         &cli.BasicUi{Writer: os.Stdout},
	}

	// CLI configuration.
	usr, err := user.Current()
	if err != nil {
		m.Ui.Error(fmt.Sprintf("Could not read current user from environment, failing."))
		return args, err
	}

	// TODO(jacobkiefer): Add flag for config location?
	configLocation := filepath.Join(usr.HomeDir, ".spin", "config")
	yamlFile, err := ioutil.ReadFile(configLocation)
	if err != nil {
		m.Ui.Error(fmt.Sprintf("Could not read configuration file from %d, failing.", configLocation))
		return args, err
	}

	err = yaml.UnmarshalStrict(yamlFile, &m.Config)
	if err != nil {
		m.Ui.Error(fmt.Sprintf("Could not deserialize config file with contents: %d, failing.", yamlFile))
		return args, err
	}

	// Api client initialization.
	cfg := &gate.Configuration{
		BasePath:      m.gateEndpoint,
		DefaultHeader: make(map[string]string),
		UserAgent:     "Spin CLI version", // TODO(jacobkiefer): Add a reasonable UserAgent.
	}
	m.GateClient = gate.NewAPIClient(cfg)

	return args, nil
}

// Colorize initializes the ui colorization.
func (m *ApiMeta) Colorize() *colorstring.Colorize {
	return &colorstring.Colorize{
		Colors:  colorstring.DefaultColors,
		Disable: !m.color,
		Reset:   true,
	}
}

func (m *ApiMeta) Help() string {
	help := `
Global Options:

	--gate-endpoint         Gate (API server) endpoint.
	`

	return strings.TrimSpace(help)
}
