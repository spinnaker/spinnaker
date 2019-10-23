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

package oauth2

import (
	"golang.org/x/oauth2"
)

// Config is the configuration for using OAuth2.0 to
// authenticate with Spinnaker
type Config struct {
	TokenUrl     string        `yaml:"tokenUrl"`
	AuthUrl      string        `yaml:"authUrl"`
	ClientId     string        `yaml:"clientId"`
	ClientSecret string        `yaml:"clientSecret"`
	Scopes       []string      `yaml:"scopes"`
	CachedToken  *oauth2.Token `yaml:"cachedToken,omitempty"`
}

func (x *Config) IsValid() bool {
	return x.TokenUrl != "" && x.AuthUrl != "" && len(x.Scopes) != 0
}
