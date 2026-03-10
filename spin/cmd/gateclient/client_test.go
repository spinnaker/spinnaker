// Copyright (c) 2018, Google, Inc.
// Copyright (c) 2019, Noel Cower.
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

package gateclient

import (
	"context"
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spinnaker/spin/config/auth"
	authoauth2 "github.com/spinnaker/spin/config/auth/oauth2"
	gate "github.com/spinnaker/spin/gateapi"
	"golang.org/x/oauth2"
)

func TestAddAuthHeaders(t *testing.T) {
	tests := []struct {
		name     string
		ctx      context.Context
		wantAuth string
	}{
		{"nil context does not add header", nil, ""},
		{"oauth2 token source sets Bearer", context.WithValue(context.Background(), gate.ContextOAuth2, oauth2.StaticTokenSource(&oauth2.Token{AccessToken: "test-token"})), "Bearer test-token"},
		{"basic auth sets Basic", context.WithValue(context.Background(), gate.ContextBasicAuth, gate.BasicAuth{UserName: "user", Password: "pass"}), "Basic " + base64.StdEncoding.EncodeToString([]byte("user:pass"))},
		{"access token sets Bearer", context.WithValue(context.Background(), gate.ContextAccessToken, "iap-token"), "Bearer iap-token"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, "http://example.com/", nil)
			err := AddAuthHeaders(tt.ctx, req)
			if err != nil {
				t.Fatalf("AddAuthHeaders() error = %v", err)
			}
			got := req.Header.Get("Authorization")
			if got != tt.wantAuth {
				t.Errorf("Authorization = %q, want %q", got, tt.wantAuth)
			}
		})
	}
}

func TestOauth2TokenSource(t *testing.T) {
	tests := []struct {
		name    string
		cfg     *auth.Config
		wantNil bool
	}{
		{"nil config", nil, true},
		{"empty config", &auth.Config{}, true},
		{"no CachedToken", &auth.Config{OAuth2: &authoauth2.Config{}}, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := oauth2TokenSource(tt.cfg)
			if (got == nil) != tt.wantNil {
				t.Errorf("oauth2TokenSource() = %v, wantNil %v", got, tt.wantNil)
			}
		})
	}

	t.Run("with CachedToken returns TokenSource", func(t *testing.T) {
		cfg := &auth.Config{
			OAuth2: &authoauth2.Config{
				AuthUrl:      "https://auth.example.com",
				TokenUrl:     "https://token.example.com",
				ClientId:     "client",
				ClientSecret: "secret",
				Scopes:       []string{"openid"},
				CachedToken:  &oauth2.Token{AccessToken: "cached"},
			},
		}
		got := oauth2TokenSource(cfg)
		if got == nil {
			t.Fatal("oauth2TokenSource() = nil, want non-nil")
		}
		tok, err := got.Token()
		if err != nil {
			t.Fatalf("Token() = %v", err)
		}
		if tok.AccessToken != "cached" {
			t.Errorf("Token().AccessToken = %q, want cached", tok.AccessToken)
		}
	})
}

// roundTripperFunc captures the request for inspection in tests.
type roundTripperFunc struct {
	fn func(*http.Request) (*http.Response, error)
}

func (r *roundTripperFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return r.fn(req)
}

func TestAuthTransport_roundTrip_addsBearer(t *testing.T) {
	var capturedReq *http.Request
	base := &roundTripperFunc{fn: func(req *http.Request) (*http.Response, error) {
		capturedReq = req
		return &http.Response{StatusCode: http.StatusOK}, nil
	}}
	tr := &authTransport{base: base, oauth2: oauth2.StaticTokenSource(&oauth2.Token{AccessToken: "transport-token"})}

	req := httptest.NewRequest(http.MethodGet, "http://example.com/foo", nil)
	resp, err := tr.RoundTrip(req)
	if err != nil {
		t.Fatalf("RoundTrip() = %v", err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Errorf("StatusCode = %d, want 200", resp.StatusCode)
	}
	if capturedReq == nil {
		t.Fatal("base RoundTripper was not called")
	}
	if got := capturedReq.Header.Get("Authorization"); got != "Bearer transport-token" {
		t.Errorf("Authorization = %q, want Bearer transport-token", got)
	}
}

func TestAuthTransport_roundTrip_usesDefaultTransport(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if auth := r.Header.Get("Authorization"); auth != "Bearer default-transport-token" {
			t.Errorf("server saw Authorization %q", auth)
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	tr := &authTransport{base: nil, oauth2: oauth2.StaticTokenSource(&oauth2.Token{AccessToken: "default-transport-token"})}
	req, _ := http.NewRequest(http.MethodGet, server.URL, nil)
	resp, err := tr.RoundTrip(req)
	if err != nil {
		t.Fatalf("RoundTrip() = %v", err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Errorf("StatusCode = %d, want 200", resp.StatusCode)
	}
}
