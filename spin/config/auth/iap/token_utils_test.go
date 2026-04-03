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
	"encoding/base64"
	"encoding/json"
	"testing"
	"time"
)

func createTestJWT(exp int64) string {
	// Header: {"alg":"RS256","typ":"JWT"}
	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"RS256","typ":"JWT"}`))

	// Payload with exp claim
	payload := map[string]interface{}{"exp": exp, "iat": exp - 3600}
	payloadBytes, _ := json.Marshal(payload)
	payloadEncoded := base64.RawURLEncoding.EncodeToString(payloadBytes)

	// Fake signature
	signature := base64.RawURLEncoding.EncodeToString([]byte("fake-signature"))

	return header + "." + payloadEncoded + "." + signature
}

func TestParseIDTokenExpiry(t *testing.T) {
	// Test with a known expiry time
	expectedExp := int64(1704067200) // Jan 1, 2024 00:00:00 UTC
	testJWT := createTestJWT(expectedExp)

	expiry, err := ParseIDTokenExpiry(testJWT)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	expected := time.Unix(expectedExp, 0)
	if !expiry.Equal(expected) {
		t.Errorf("expected %v, got %v", expected, expiry)
	}
}

func TestParseIDTokenExpiry_InvalidFormat(t *testing.T) {
	tests := []struct {
		name  string
		token string
	}{
		{"empty", ""},
		{"one part", "header"},
		{"two parts", "header.payload"},
		{"four parts", "a.b.c.d"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := ParseIDTokenExpiry(tt.token)
			if err == nil {
				t.Error("expected error for invalid JWT format")
			}
		})
	}
}

func TestParseIDTokenExpiry_MissingExpClaim(t *testing.T) {
	// JWT with no exp claim
	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"RS256"}`))
	payload := base64.RawURLEncoding.EncodeToString([]byte(`{"sub":"test"}`))
	signature := base64.RawURLEncoding.EncodeToString([]byte("sig"))

	token := header + "." + payload + "." + signature

	_, err := ParseIDTokenExpiry(token)
	if err == nil {
		t.Error("expected error for missing exp claim")
	}
}

func TestCachedIDToken_IsValid_Nil(t *testing.T) {
	var nilToken *CachedIDToken
	if nilToken.IsValid() {
		t.Error("nil token should not be valid")
	}
}

func TestCachedIDToken_IsValid_EmptyToken(t *testing.T) {
	token := &CachedIDToken{
		IDToken: "",
		Expiry:  time.Now().Add(1 * time.Hour),
	}
	if token.IsValid() {
		t.Error("empty token should not be valid")
	}
}

func TestCachedIDToken_IsValid_Expired(t *testing.T) {
	token := &CachedIDToken{
		IDToken: "test-token",
		Expiry:  time.Now().Add(-1 * time.Hour),
	}
	if token.IsValid() {
		t.Error("expired token should not be valid")
	}
}

func TestCachedIDToken_IsValid_ExpiringWithinBuffer(t *testing.T) {
	// Token expiring in 5 seconds (within the 10-second buffer)
	token := &CachedIDToken{
		IDToken: "test-token",
		Expiry:  time.Now().Add(5 * time.Second),
	}
	if token.IsValid() {
		t.Error("token expiring within buffer should not be valid")
	}
}

func TestCachedIDToken_IsValid_Valid(t *testing.T) {
	token := &CachedIDToken{
		IDToken: "test-token",
		Expiry:  time.Now().Add(1 * time.Hour),
	}
	if !token.IsValid() {
		t.Error("valid token should be valid")
	}
}

func TestNewCachedIDToken(t *testing.T) {
	expectedExp := time.Now().Add(1 * time.Hour).Unix()
	testJWT := createTestJWT(expectedExp)

	cached, err := NewCachedIDToken(testJWT)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cached.IDToken != testJWT {
		t.Error("token should match input")
	}

	if !cached.Expiry.Equal(time.Unix(expectedExp, 0)) {
		t.Errorf("expiry mismatch: got %v, want %v", cached.Expiry, time.Unix(expectedExp, 0))
	}
}

func TestNewCachedIDToken_InvalidJWT(t *testing.T) {
	_, err := NewCachedIDToken("invalid-jwt")
	if err == nil {
		t.Error("expected error for invalid JWT")
	}
}
