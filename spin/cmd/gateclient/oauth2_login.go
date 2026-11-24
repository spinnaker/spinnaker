// Copyright (c) 2026, Spinnaker Authors.
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
	"bufio"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/pkg/errors"
	"golang.org/x/oauth2"

	"github.com/spinnaker/spinnaker/spin/util/execcmd"
)

// oauth2RedirectURL is the redirect URI spin registers with the OAuth2
// provider. The port is fixed because some providers (for example Okta) match
// loopback redirect URIs including the port.
const oauth2RedirectURL = "http://localhost:8085"

// oauth2CallbackAddrs are the loopback addresses spin listens on for the
// redirect. "localhost" may resolve to either address, so spin listens on
// both. Only the first one is required; the IPv6 loopback may be unavailable.
var oauth2CallbackAddrs = []string{"127.0.0.1:8085", "[::1]:8085"}

// openBrowser opens a URL in the user's browser. Tests replace it.
var openBrowser = execcmd.OpenUrl

// authCodeResult is the outcome of the browser redirect or the manual paste.
type authCodeResult struct {
	code string
	err  error
}

// listenForOAuth2Callback opens the loopback listeners for the redirect. It
// fails if the first address can't be bound, for example because another
// process already uses the port, and skips later addresses that can't be bound.
func listenForOAuth2Callback(addrs []string) ([]net.Listener, error) {
	var listeners []net.Listener
	for i, addr := range addrs {
		listener, err := net.Listen("tcp", addr)
		if err != nil {
			if i == 0 {
				return nil, errors.Wrapf(err, "could not listen on %s for the OAuth2 redirect, is another process using that port?", addr)
			}
			continue
		}
		listeners = append(listeners, listener)
	}
	return listeners, nil
}

// authorizationCodeURL builds the provider's authorization URL with PKCE.
func authorizationCodeURL(config *oauth2.Config, state, codeChallenge string, forceConsent bool) string {
	opts := []oauth2.AuthCodeOption{
		oauth2.AccessTypeOffline,
		oauth2.SetAuthURLParam("code_challenge_method", "S256"),
		oauth2.SetAuthURLParam("code_challenge", codeChallenge),
	}
	if forceConsent {
		opts = append(opts, oauth2.ApprovalForce)
	}
	return config.AuthCodeURL(state, opts...)
}

// newOAuth2CallbackHandler handles the provider's redirect to the loopback
// listener. It only serves "/", rejects requests whose state doesn't match, and
// delivers the first code or provider error to results.
func newOAuth2CallbackHandler(state string, results chan<- authCodeResult) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		query := r.URL.Query()
		if subtle.ConstantTimeCompare([]byte(query.Get("state")), []byte(state)) != 1 {
			http.Error(w, "Invalid or missing state parameter. Start the login again from spin.", http.StatusBadRequest)
			return
		}
		if providerError := query.Get("error"); providerError != "" {
			deliverAuthCodeResult(results, authCodeResult{
				err: fmt.Errorf("OAuth2 provider returned error %q: %q", providerError, query.Get("error_description")),
			})
			http.Error(w, "Sign-in failed. See spin's output for details. You can close this tab.", http.StatusBadRequest)
			return
		}
		code := query.Get("code")
		if code == "" {
			http.Error(w, "Missing authorization code. Start the login again from spin.", http.StatusBadRequest)
			return
		}
		deliverAuthCodeResult(results, authCodeResult{code: code})
		fmt.Fprintln(w, "Signed in to spin. You can close this tab and return to the terminal.")
	})
	return mux
}

// deliverAuthCodeResult sends result unless one was already delivered.
func deliverAuthCodeResult(results chan<- authCodeResult, result authCodeResult) {
	select {
	case results <- result:
	default:
	}
}

// readPastedAuthCode delivers the first non-empty line read from paste. It
// delivers nothing if paste ends without one, so the browser redirect can
// still complete the login.
func readPastedAuthCode(paste io.Reader, results chan<- authCodeResult) {
	reader := bufio.NewReader(paste)
	for {
		line, err := reader.ReadString('\n')
		if code := strings.TrimSpace(line); code != "" {
			deliverAuthCodeResult(results, authCodeResult{code: code})
			return
		}
		if err != nil {
			return
		}
	}
}

// runAuthorizationCodeFlow runs the OAuth2 authorization code flow with PKCE.
// It prints the authorization URL and tries to open it in the browser. The code
// arrives through the browser redirect to listeners or, when paste is
// not nil, as a pasted line (for browsers that can't reach the loopback
// listener, for example over SSH). It closes the listeners before returning.
func runAuthorizationCodeFlow(output func(string), config *oauth2.Config, forceConsent bool, listeners []net.Listener, paste io.Reader) (*oauth2.Token, error) {
	results := make(chan authCodeResult, 1)
	server := &http.Server{ReadHeaderTimeout: 10 * time.Second}
	defer func() {
		server.Close()
		for _, listener := range listeners {
			listener.Close()
		}
	}()

	stateBytes := make([]byte, 32)
	if _, err := rand.Read(stateBytes); err != nil {
		return nil, errors.Wrap(err, "Could not generate random string for state")
	}
	state := base64.RawURLEncoding.EncodeToString(stateBytes)

	verifier, codeChallenge, err := generateCodeVerifier()
	if err != nil {
		return nil, err
	}

	server.Handler = newOAuth2CallbackHandler(state, results)
	for _, listener := range listeners {
		go server.Serve(listener)
	}

	authURL := authorizationCodeURL(config, state, codeChallenge, forceConsent)
	output(fmt.Sprintf("Navigate to %s and authenticate", authURL))
	// Try to open the browser as well. The link above stays the fallback when
	// that fails, for example over SSH. Don't wait for it: on some systems the
	// opener only returns once the browser exits.
	open := openBrowser
	go func() { _ = open(authURL) }()
	if paste != nil {
		output("Waiting for the browser to return to spin. If your browser can't reach localhost, paste the authorization code here:")
		go readPastedAuthCode(paste, results)
	} else {
		output("Waiting for the browser to return to spin...")
	}

	result := <-results
	if result.err != nil {
		return nil, result.err
	}
	return config.Exchange(context.Background(), result.code, oauth2.SetAuthURLParam("code_verifier", verifier))
}
