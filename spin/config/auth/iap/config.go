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

import "time"

// CachedIDToken stores a cached IAP ID token with its expiration time.
type CachedIDToken struct {
	IDToken string    `json:"idToken" yaml:"idToken"`
	Expiry  time.Time `json:"expiry" yaml:"expiry"`
}

// IsValid returns true if the token exists and is not expired.
// Uses a 10-second buffer before actual expiry to avoid edge cases.
func (t *CachedIDToken) IsValid() bool {
	if t == nil || t.IDToken == "" {
		return false
	}
	return time.Now().Add(10 * time.Second).Before(t.Expiry)
}

// Config mapping to the config file for IAP
type Config struct {
	OAuthClientId         string         `json:"oauthClientId" yaml:"oauthClientId"`
	OAuthClientSecret     string         `json:"oauthClientSecret" yaml:"oauthClientSecret"`
	IapClientId           string         `json:"iapClientId" yaml:"iapClientId"`
	IapClientRefresh      string         `json:"iapClientRefresh" yaml:"iapClientRefresh"`
	IapIdToken            string         `json:"iapIdToken" yaml:"iapIdToken"`
	ServiceAccountKeyPath string         `json:"serviceAccountKeyPath" yaml:"serviceAccountKeyPath"`
	CachedToken           *CachedIDToken `json:"cachedToken,omitempty" yaml:"cachedToken,omitempty"`
}
