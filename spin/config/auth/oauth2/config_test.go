// Copyright (c) 2026, Spinnaker.io
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
	"testing"

	"sigs.k8s.io/yaml"
)

func boolPtr(b bool) *bool {
	return &b
}

func TestShouldForceConsent(t *testing.T) {
	tests := []struct {
		name         string
		forceConsent *bool
		want         bool
	}{
		{name: "unset defaults to true", forceConsent: nil, want: true},
		{name: "explicit true", forceConsent: boolPtr(true), want: true},
		{name: "explicit false", forceConsent: boolPtr(false), want: false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cfg := &Config{ForceConsent: tt.forceConsent}
			if got := cfg.ShouldForceConsent(); got != tt.want {
				t.Errorf("ShouldForceConsent() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestShouldForceConsentFromYAML(t *testing.T) {
	tests := []struct {
		name string
		yaml string
		want bool
	}{
		{
			name: "forceConsent false",
			yaml: "forceConsent: false\nauthUrl: a\ntokenUrl: t\nscopes: [openid]\n",
			want: false,
		},
		{
			name: "forceConsent absent",
			yaml: "authUrl: a\ntokenUrl: t\nscopes: [openid]\n",
			want: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var cfg Config
			if err := yaml.UnmarshalStrict([]byte(tt.yaml), &cfg); err != nil {
				t.Fatalf("UnmarshalStrict returned error: %v", err)
			}
			if got := cfg.ShouldForceConsent(); got != tt.want {
				t.Errorf("ShouldForceConsent() = %v, want %v", got, tt.want)
			}
		})
	}
}
