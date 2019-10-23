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

	"github.com/spinnaker/spin/util/execcmd"
	"golang.org/x/oauth2"
)

const (
	googleOauthHost         = "accounts.google.com"
	googleOauthPath         = "/o/oauth2/v2/auth"
	googleOauthSchema       = "https"
	googleOauthResponseType = "code"
	googleOauthScope        = "openid email"
)

// returns the token get from google for IAP
func GetIapToken(iapConfig Config) (string, error) {
	if iapConfig.IapIdToken != "" {
		return iapConfig.IapIdToken, nil
	}

	if iapConfig.ServiceAccountKeyPath != "" && iapConfig.IapClientId != "" {
		return GetIDTokenWithServiceAccount(iapConfig)
	}

	if iapConfig.IapClientRefresh == "" {
		return userInteract(iapConfig)
	}

	return RequestIapIDToken(iapConfig.IapClientRefresh,
		iapConfig.OAuthClientId,
		iapConfig.OAuthClientSecret,
		iapConfig.IapClientId)
}

// userInteract lets the spin user fetch a token.
func userInteract(cfg Config) (string, error) {
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
	oauthUrl.RawQuery = q.Encode()

	return oauthUrl.String()
}
