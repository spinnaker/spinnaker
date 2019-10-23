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

package x509

// Config is the configuration for using X.509 certs to
// authenticate with Spinnaker.
type Config struct {
	CertPath string `yaml:"certPath"`
	KeyPath  string `yaml:"keyPath"`
	Cert     string `yaml:"cert"` // Cert is base64 encoded PEM block.
	Key      string `yaml:"key"`  // Key is base64 encoded PEM block.
}

func (x *Config) IsValid() bool {
	// Only one pair of configs properties should be set.
	pathPropertySet := x.CertPath != "" || x.KeyPath != ""
	pemPropertySet := x.Cert != "" || x.Key != ""
	if pathPropertySet && pemPropertySet {
		return false
	}

	pathPropertyMismatch := (x.CertPath != "" && x.KeyPath == "") || (x.KeyPath != "" && x.CertPath == "")
	pemPropertyMismatch := (x.Cert != "" && x.Key == "") || (x.Key != "" && x.Cert == "")
	if pathPropertyMismatch || pemPropertyMismatch {
		return false
	}

	return true
}
