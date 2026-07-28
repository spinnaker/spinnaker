// Copyright (c) 2026, Himanshu Gusain.
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

import (
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// ParseIDTokenExpiry extracts the expiration time from a JWT ID token.
func ParseIDTokenExpiry(idToken string) (time.Time, error) {
	token, _, err := jwt.NewParser().ParseUnverified(idToken, jwt.MapClaims{})
	if err != nil {
		return time.Time{}, fmt.Errorf("failed to parse JWT: %v", err)
	}

	expiry, err := token.Claims.GetExpirationTime()
	if err != nil || expiry == nil {
		return time.Time{}, fmt.Errorf("JWT missing exp claim")
	}

	return expiry.Time, nil
}

// NewCachedIDToken creates a CachedIDToken from a raw ID token string.
// It parses the JWT to extract the expiration time.
func NewCachedIDToken(idToken string) (*CachedIDToken, error) {
	expiry, err := ParseIDTokenExpiry(idToken)
	if err != nil {
		return nil, err
	}
	return &CachedIDToken{
		IDToken: idToken,
		Expiry:  expiry,
	}, nil
}
