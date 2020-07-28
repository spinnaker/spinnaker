// Copyright (c) 2018, Snap Inc.
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

package config

// Config mapping to the config file for IAP
type Config struct {
	OAuthClientId         string `yaml:"oauthClientId"`
	OAuthClientSecret     string `yaml:"oauthClientSecret"`
	IapClientId           string `yaml:"iapClientId"`
	IapClientRefresh      string `yaml:"iapClientRefresh"`
	IapIdToken            string `yaml:"iapIdToken"`
	ServiceAccountKeyPath string `yaml:"serviceAccountKeyPath"`
}
