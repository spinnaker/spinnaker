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
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"html/template"
	"log"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

const (
	googleTokenURL      = "https://www.googleapis.com/oauth2/v4/token"
	oauthEmailScope     = "https://www.googleapis.com/auth/userinfo.email"
	serverStateTokenLen = 60
)

// Configure the default request scopes, this can be overriden
var oauthScopes = []string{oauthEmailScope}

// Creates a new iapOAuthResponse object
type iapOAuthResponse struct {
	AccessToken  string `json:"access_token,omitempty"`
	RefreshToken string `json:"refresh_token,omitempty"`
	Type         string `json:"token_type,omitempty"`
	Expires      int    `json:"expires_in,omitempty"`
	IDToken      string `json:"id_token,omitempty"`
}

type oauthReceiver struct {
	port         int
	clientState  string
	killMeNow    bool
	mainClient   string
	doneChan     chan error
	callback     func(*oauth2.Token, *oauth2.Config, string) (string, error)
	clientId     string
	clientSecret string
}

// Cleanly exit the HTTP server.
func (o *oauthReceiver) killWhenReady(n net.Conn, s http.ConnState) {
	if o.killMeNow && s.String() == "idle" && o.mainClient == n.RemoteAddr().String() {
		o.doneChan <- nil
	}
}

// NewOAuthConfig creates a new OAuth config
func (o *oauthReceiver) NewOAuthConfig() *oauth2.Config {
	return &oauth2.Config{
		ClientID:     o.clientId,
		ClientSecret: o.clientSecret,
		RedirectURL:  fmt.Sprintf("http://localhost:%d", o.port),
		Scopes:       oauthScopes,
		Endpoint:     google.Endpoint,
	}
}

// check the iap's state
func ValidIAPStateToken(state, clientState string) bool {
	return state == clientState
}

// ServeHTTP will serve an HTTP server on localhost used to receive the OAuth callback
func (o *oauthReceiver) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	var tok *oauth2.Token
	var conf *oauth2.Config

	defer func() {
		r := recover()
		if r != nil {
			log.Printf("panic: %s\n", r)
			o.doneChan <- fmt.Errorf("%v", r)
		}
	}()
	if r.URL.String() == "/favicon.ico" {
		return
	}
	if r.URL.String() == "/robots.txt" {
		return
	}

	o.mainClient = r.RemoteAddr

	conf = o.NewOAuthConfig()
	oauthCode := r.FormValue("code")
	state := r.FormValue("state")
	if oauthCode == "" || state == "" {
		response := fmt.Errorf("Invalid response from Google's Account Service")
		o.WebOutput(w, "Uh Oh!", response.Error())
		o.doneChan <- response
		return
	}

	validateStateFunc := ValidIAPStateToken

	if !validateStateFunc(state, o.clientState) {
		response := fmt.Errorf("Invalid state token received from server")
		o.WebOutput(w, "Uh Oh!", response.Error())
		o.doneChan <- response
		return
	}

	tok, err := conf.Exchange(context.Background(), oauthCode)
	if err != nil {
		response := fmt.Errorf("Received invalid OAUTH token code: %v", err)
		o.WebOutput(w, "Uh Oh!", response.Error())
		o.doneChan <- response
	}

	msg, err := o.callback(tok, conf, state)
	if err != nil {
		o.WebOutput(w, "Uh Oh!", err.Error())
	} else {
		o.WebOutput(w, "Successfully authenticated to Spinnaker!", msg)
	}
	o.killMeNow = true
}

// WebOutput takes a header and message and displays them in a simple template
// replacing the old plain white response page :)
func (o *oauthReceiver) WebOutput(w http.ResponseWriter, header string, msg string) {
	// Create basic template
	t, err := template.New("base").Parse(defaultTemplate)
	if err != nil {
		fmt.Fprint(w, err)
	}
	err = t.Execute(w, map[string]interface{}{"header": header, "messages": strings.Split(msg, "\n")})
	if err != nil {
		fmt.Fprint(w, err)
	}
}

// RequestIapIDToken implements the audience parameter required for accessing
// IAP, see for more https://cloud.google.com/iap/docs/authentication-howto
func RequestIapIDToken(token string, clientId string, clientSecret string, iapClientId string) (string, error) {
	body := url.Values{}
	body.Set("client_id", clientId)
	body.Add("client_secret", clientSecret)
	body.Add("refresh_token", token)
	body.Add("grant_type", "refresh_token")
	body.Add("audience", iapClientId)

	// Create request to google
	req, err := http.NewRequest("POST", googleTokenURL, bytes.NewBufferString(body.Encode()))
	if err != nil {
		return "", fmt.Errorf("Invalid request crafted when preparing IAP id_token, err: %s", err)
	}
	req.Header.Add("Content-Type", "application/x-www-form-urlencoded")

	client := http.Client{
		Timeout: time.Second * 30,
	}
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("Unable to exchange access_token for id_token with audience, err: %s", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("Invalid response received when getting IAP id_token, err: %s", err)
	}

	// Create a one time response struct
	oauthResponse := &iapOAuthResponse{}
	// Decode the response
	err = json.NewDecoder(resp.Body).Decode(&oauthResponse)
	if err != nil {
		return "", fmt.Errorf("Invalid response received when decoding IAP id_token, err: %s", err)
	}

	// Check that it's not empty/blank
	if len(oauthResponse.IDToken) <= 0 {
		return "", fmt.Errorf("Invalid ID Token returned")
	}

	// return
	return oauthResponse.IDToken, nil
}
