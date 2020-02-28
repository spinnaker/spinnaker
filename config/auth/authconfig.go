// Copyright (c) 2018, Google, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package auth

import (
	"github.com/spinnaker/spin/config/auth/basic"
	gsa "github.com/spinnaker/spin/config/auth/googleserviceaccount"
	config "github.com/spinnaker/spin/config/auth/iap"
	"github.com/spinnaker/spin/config/auth/ldap"
	"github.com/spinnaker/spin/config/auth/oauth2"
	"github.com/spinnaker/spin/config/auth/x509"
)

// Config is the CLI's authentication configuration.
type Config struct {
	Enabled bool           `yaml:"enabled"`
	X509    *x509.Config   `yaml:"x509,omitempty"`
	OAuth2  *oauth2.Config `yaml:"oauth2,omitempty"`
	Basic   *basic.Config  `yaml:"basic,omitempty"`
	Iap     *config.Config `yaml:"iap,omitempty"`
	Ldap    *ldap.Config   `yaml:"ldap,omitempty"`

	GoogleServiceAccount *gsa.Config `yaml:"google_service_account,omitempty"`
}
