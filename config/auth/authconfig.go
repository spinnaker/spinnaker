package auth

import (
	"github.com/spinnaker/spin/config/auth/x509"
)

// AuthConfig is the CLI's authentication configuration.
type AuthConfig struct {
	Enabled bool             `yaml:"enabled"`
	X509    *x509.X509Config `yaml:"x509"`
}
