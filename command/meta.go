package command

import (
	"crypto/tls"
	"crypto/x509"
	"errors"
	"flag"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/cookiejar"
	"os"
	"os/user"
	"path/filepath"
	"strings"

	"github.com/mitchellh/cli"
	"github.com/mitchellh/colorstring"
	"github.com/mitchellh/go-homedir"
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
		m.Ui.Warn(fmt.Sprintf("Could not read configuration file from %s.", configLocation))
	}

	if yamlFile != nil {
		err = yaml.UnmarshalStrict(yamlFile, &m.Config)
		if err != nil {
			m.Ui.Error(fmt.Sprintf("Could not deserialize config file with contents: %d, failing.", yamlFile))
			return args, err
		}
	} else {
		m.Config = config.Config{}
	}

	// Api client initialization.
	client, err := m.InitializeClient()
	if err != nil {
		m.Ui.Error(fmt.Sprintf("Could not initialize http client, failing."))
		return args, err
	}

	cfg := &gate.Configuration{
		BasePath:      m.gateEndpoint,
		DefaultHeader: make(map[string]string),
		UserAgent:     "Spin CLI version", // TODO(jacobkiefer): Add a reasonable UserAgent.
		HTTPClient:    client,
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

func (m *ApiMeta) InitializeClient() (*http.Client, error) {
	auth := m.Config.Auth
	cookieJar, _ := cookiejar.New(nil)
	client := http.Client{
		Jar: cookieJar,
	}

	if auth != nil && auth.Enabled && auth.X509 != nil {
		X509 := auth.X509
		client.Transport = &http.Transport{
			TLSClientConfig: &tls.Config{},
		}

		if !X509.IsValid() {
			// Misconfigured.
			return nil, errors.New("Incorrect x509 auth configuration.\nMust specify certPath/keyPath or cert/key pair.")
		}

		if X509.CertPath != "" && X509.KeyPath != "" {
			certPath, err := homedir.Expand(X509.CertPath)
			if err != nil {
				return nil, err
			}
			keyPath, err := homedir.Expand(X509.KeyPath)
			if err != nil {
				return nil, err
			}

			cert, err := tls.LoadX509KeyPair(certPath, keyPath)
			if err != nil {
				return nil, err
			}

			clientCA, err := ioutil.ReadFile(certPath)
			if err != nil {
				return nil, err
			}

			return m.initializeX509Config(client, clientCA, cert), nil
		} else if X509.Cert != "" && X509.Key != "" {
			certBytes := []byte(X509.Cert)
			keyBytes := []byte(X509.Key)
			cert, err := tls.X509KeyPair(certBytes, keyBytes)
			if err != nil {
				return nil, err
			}

			return m.initializeX509Config(client, certBytes, cert), nil
		} else {
			// Misconfigured.
			return nil, errors.New("Incorrect x509 auth configuration.\nMust specify certPath/keyPath or cert/key pair.")
		}
	} else {
		return &client, nil
	}
}

func (m *ApiMeta) initializeX509Config(client http.Client, clientCA []byte, cert tls.Certificate) *http.Client {
	clientCertPool := x509.NewCertPool()
	clientCertPool.AppendCertsFromPEM(clientCA)

	client.Transport.(*http.Transport).TLSClientConfig.MinVersion = tls.VersionTLS12
	client.Transport.(*http.Transport).TLSClientConfig.PreferServerCipherSuites = true
	client.Transport.(*http.Transport).TLSClientConfig.Certificates = []tls.Certificate{cert}
	client.Transport.(*http.Transport).TLSClientConfig.InsecureSkipVerify = true // TODO(jacobkiefer): Add a flag this.

	return &client
}

func (m *ApiMeta) Help() string {
	help := `
Global Options:

	--gate-endpoint         Gate (API server) endpoint.
        --no-color              Removes color from CLI output.
	`

	return strings.TrimSpace(help)
}
