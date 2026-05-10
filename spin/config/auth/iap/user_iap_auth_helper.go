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

import (
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"net"
	"net/http"
	"net/url"

	"golang.org/x/oauth2"

	"github.com/spinnaker/spin/util/execcmd"
)

const (
	googleOauthHost         = "accounts.google.com"
	googleOauthPath         = "/o/oauth2/v2/auth"
	googleOauthSchema       = "https"
	googleOauthResponseType = "code"
	googleOauthScope        = "openid email"
)

// GetIapToken returns the IAP ID token, using cache if valid.
// Returns: token string, whether config was updated (for caching), error.
func GetIapToken(iapConfig *Config) (string, bool, error) {
	// Priority 1: Manually configured token (backwards compatibility)
	if iapConfig.IapIdToken != "" {
		return iapConfig.IapIdToken, false, nil
	}

	// Priority 2: Check cached token validity
	if iapConfig.CachedToken != nil && iapConfig.CachedToken.IsValid() {
		return iapConfig.CachedToken.IDToken, false, nil
	}

	// Need to fetch a new token
	var idToken string
	var err error

	if iapConfig.ServiceAccountKeyPath != "" && iapConfig.IapClientId != "" {
		idToken, err = GetIDTokenWithServiceAccount(*iapConfig)
	} else if iapConfig.IapClientRefresh == "" {
		idToken, err = userInteract(iapConfig)
	} else {
		idToken, err = RequestIapIDToken(
			iapConfig.IapClientRefresh,
			iapConfig.OAuthClientId,
			iapConfig.OAuthClientSecret,
			iapConfig.IapClientId,
		)
	}

	if err != nil {
		return "", false, err
	}

	// Cache the new token
	cachedToken, cacheErr := NewCachedIDToken(idToken)
	if cacheErr != nil {
		// Token works but we couldn't parse expiry - still return it but don't cache
		return idToken, false, nil
	}

	iapConfig.CachedToken = cachedToken
	return idToken, true, nil
}

// userInteract lets the spin user fetch a token via interactive OAuth flow.
// It also captures the refresh token for future use.
func userInteract(cfg *Config) (string, error) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return "", err
	}

	port := listener.Addr().(*net.TCPAddr).Port
	clientStateToken := make([]byte, serverStateTokenLen)
	if _, err = rand.Read(clientStateToken); err != nil {
		return "", err
	}

	clientState := base64.URLEncoding.EncodeToString(clientStateToken)
	accessToken := make(chan string)

	rcv := &oauthReceiver{
		port:        port,
		clientState: clientState,
		doneChan:    make(chan error),
		callback: func(token *oauth2.Token, config *oauth2.Config, s2 string) (string, error) {
			// Store the refresh token for future use
			if token.RefreshToken != "" {
				cfg.IapClientRefresh = token.RefreshToken
			}

			iapToken, err := RequestIapIDToken(token.AccessToken,
				cfg.OAuthClientId,
				cfg.OAuthClientSecret,
				cfg.IapClientId)
			if err != nil {
				close(accessToken)
				return "", err
			}
			accessToken <- iapToken
			return "", nil
		},
		clientId:     cfg.OAuthClientId,
		clientSecret: cfg.OAuthClientSecret,
	}

	srv := http.Server{Addr: listener.Addr().String(), Handler: rcv, ConnState: rcv.killWhenReady}
	go srv.Serve(listener)

	url := oauthURL(cfg.OAuthClientId, clientState, port)

	resStr := fmt.Sprintf("Your browser has been opened to visit:\n%s\n\n", url)
	if err = execcmd.OpenUrl(url); err != nil {
		resStr = fmt.Sprintf("Follow this link in your browser:\n%s\n\n", url)
	}
	fmt.Println(resStr)

	return <-accessToken, nil
}

func oauthURL(clientId string, clientState string, port int) string {
	oauthUrl := url.URL{
		Scheme: googleOauthSchema,
		Host:   googleOauthHost,
		Path:   googleOauthPath,
	}

	q := oauthUrl.Query()
	q.Add("client_id", clientId)
	q.Add("state", clientState)
	q.Add("response_type", googleOauthResponseType)
	q.Add("scope", googleOauthScope)
	q.Add("redirect_uri", fmt.Sprintf("http://localhost:%d", port))
	q.Add("access_type", "offline") // Request refresh token for future use
	oauthUrl.RawQuery = q.Encode()

	return oauthUrl.String()
}
