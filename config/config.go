package config

import (
	"github.com/spinnaker/spin/config/auth"
)

// Config is the CLI configuration kept in '~/.spin/config'.
type Config struct {
	Auth auth.AuthConfig `yaml:"auth"`
}
