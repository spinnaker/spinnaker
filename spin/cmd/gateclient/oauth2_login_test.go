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
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"golang.org/x/oauth2"
)

const testFlowTimeout = 5 * time.Second

// TestMain keeps the tests in this package from opening a real browser.
func TestMain(m *testing.M) {
	openBrowser = func(string) error { return nil }
	os.Exit(m.Run())
}

func testAuthConfig(tokenURL string) *oauth2.Config {
	return &oauth2.Config{
		ClientID:    "cid",
		RedirectURL: oauth2RedirectURL,
		Endpoint: oauth2.Endpoint{
			AuthURL:  "https://idp.example/authorize",
			TokenURL: tokenURL,
		},
	}
}

func TestAuthorizationCodeURLForceConsent(t *testing.T) {
	config := testAuthConfig("https://idp.example/token")

	raw := authorizationCodeURL(config, "the-state", "the-challenge", true)
	u, err := url.Parse(raw)
	if err != nil {
		t.Fatalf("authorizationCodeURL returned unparseable URL %q: %v", raw, err)
	}
	q := u.Query()

	if got := q.Get("prompt"); got != "consent" {
		t.Errorf("prompt: got %q, want %q", got, "consent")
	}
	assertAuthorizationQuery(t, q)
}

func TestAuthorizationCodeURLWithoutForceConsent(t *testing.T) {
	config := testAuthConfig("https://idp.example/token")

	raw := authorizationCodeURL(config, "the-state", "the-challenge", false)
	u, err := url.Parse(raw)
	if err != nil {
		t.Fatalf("authorizationCodeURL returned unparseable URL %q: %v", raw, err)
	}
	q := u.Query()

	if _, ok := q["prompt"]; ok {
		t.Errorf("prompt: expected no prompt parameter, got %q", q.Get("prompt"))
	}
	assertAuthorizationQuery(t, q)
}

func assertAuthorizationQuery(t *testing.T, q url.Values) {
	t.Helper()
	want := map[string]string{
		"state":                 "the-state",
		"code_challenge":        "the-challenge",
		"code_challenge_method": "S256",
		"access_type":           "offline",
		"redirect_uri":          oauth2RedirectURL,
		"client_id":             "cid",
		"response_type":         "code",
	}
	for key, value := range want {
		if got := q.Get(key); got != value {
			t.Errorf("%s: got %q, want %q", key, got, value)
		}
	}
}

// serveCallback sends a GET for target to handler and returns the recorded
// response.
func serveCallback(handler http.Handler, target string) *httptest.ResponseRecorder {
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, target, nil))
	return rec
}

// receiveNow returns a delivered result without blocking.
func receiveNow(results <-chan authCodeResult) (authCodeResult, bool) {
	select {
	case result := <-results:
		return result, true
	default:
		return authCodeResult{}, false
	}
}

func assertNothingDelivered(t *testing.T, results <-chan authCodeResult) {
	t.Helper()
	if result, ok := receiveNow(results); ok {
		t.Fatalf("expected no result to be delivered, got code=%q err=%v", result.code, result.err)
	}
}

func TestOAuth2CallbackHandlerRejectsWrongState(t *testing.T) {
	results := make(chan authCodeResult, 1)
	handler := newOAuth2CallbackHandler("expected-state", results)

	rec := serveCallback(handler, "/?state=wrong-state&code=abc")

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status: got %d, want %d", rec.Code, http.StatusBadRequest)
	}
	assertNothingDelivered(t, results)
}

func TestOAuth2CallbackHandlerRejectsMissingState(t *testing.T) {
	results := make(chan authCodeResult, 1)
	handler := newOAuth2CallbackHandler("expected-state", results)

	rec := serveCallback(handler, "/?code=abc")

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status: got %d, want %d", rec.Code, http.StatusBadRequest)
	}
	assertNothingDelivered(t, results)
}

func TestOAuth2CallbackHandlerDeliversProviderError(t *testing.T) {
	results := make(chan authCodeResult, 1)
	handler := newOAuth2CallbackHandler("expected-state", results)

	rec := serveCallback(handler, "/?state=expected-state&error=access_denied&error_description=x")

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status: got %d, want %d", rec.Code, http.StatusBadRequest)
	}
	result, ok := receiveNow(results)
	if !ok {
		t.Fatal("expected provider error to be delivered, got nothing")
	}
	if result.err == nil {
		t.Fatalf("expected non-nil err, got code=%q", result.code)
	}
	if !strings.Contains(result.err.Error(), "access_denied") {
		t.Errorf("err %q does not contain %q", result.err.Error(), "access_denied")
	}
}

func TestOAuth2CallbackHandlerRejectsMissingCode(t *testing.T) {
	results := make(chan authCodeResult, 1)
	handler := newOAuth2CallbackHandler("expected-state", results)

	rec := serveCallback(handler, "/?state=expected-state")

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status: got %d, want %d", rec.Code, http.StatusBadRequest)
	}
	assertNothingDelivered(t, results)
}

func TestOAuth2CallbackHandlerDeliversCode(t *testing.T) {
	results := make(chan authCodeResult, 1)
	handler := newOAuth2CallbackHandler("expected-state", results)

	rec := serveCallback(handler, "/?state=expected-state&code=abc")

	if rec.Code != http.StatusOK {
		t.Errorf("status: got %d, want %d", rec.Code, http.StatusOK)
	}
	result, ok := receiveNow(results)
	if !ok {
		t.Fatal("expected code to be delivered, got nothing")
	}
	if result.err != nil {
		t.Fatalf("unexpected err: %v", result.err)
	}
	if result.code != "abc" {
		t.Errorf("code: got %q, want %q", result.code, "abc")
	}
}

func TestOAuth2CallbackHandlerNotFoundForOtherPaths(t *testing.T) {
	results := make(chan authCodeResult, 1)
	handler := newOAuth2CallbackHandler("expected-state", results)

	rec := serveCallback(handler, "/debug/pprof/?state=expected-state&code=abc")

	if rec.Code != http.StatusNotFound {
		t.Errorf("status: got %d, want %d", rec.Code, http.StatusNotFound)
	}
	assertNothingDelivered(t, results)
}

func TestOAuth2CallbackHandlerKeepsFirstCodeAndDoesNotBlock(t *testing.T) {
	results := make(chan authCodeResult, 1)
	handler := newOAuth2CallbackHandler("expected-state", results)

	done := make(chan struct{})
	go func() {
		defer close(done)
		serveCallback(handler, "/?state=expected-state&code=first")
		serveCallback(handler, "/?state=expected-state&code=second")
	}()
	select {
	case <-done:
	case <-time.After(testFlowTimeout):
		t.Fatal("second callback request blocked")
	}

	result, ok := receiveNow(results)
	if !ok {
		t.Fatal("expected first code to be delivered, got nothing")
	}
	if result.code != "first" {
		t.Errorf("code: got %q, want %q", result.code, "first")
	}
	assertNothingDelivered(t, results)
}

func TestReadPastedAuthCodeSkipsBlankLines(t *testing.T) {
	results := make(chan authCodeResult, 1)

	readPastedAuthCode(strings.NewReader("\n  \ncode123\n"), results)

	result, ok := receiveNow(results)
	if !ok {
		t.Fatal("expected pasted code to be delivered, got nothing")
	}
	if result.code != "code123" {
		t.Errorf("code: got %q, want %q", result.code, "code123")
	}
}

func TestReadPastedAuthCodeEOFDeliversNothing(t *testing.T) {
	results := make(chan authCodeResult, 1)

	readPastedAuthCode(strings.NewReader(""), results)

	assertNothingDelivered(t, results)
}

func TestReadPastedAuthCodeAcceptsLineWithoutNewline(t *testing.T) {
	results := make(chan authCodeResult, 1)

	readPastedAuthCode(strings.NewReader("tail-without-newline"), results)

	result, ok := receiveNow(results)
	if !ok {
		t.Fatal("expected pasted code to be delivered, got nothing")
	}
	if result.code != "tail-without-newline" {
		t.Errorf("code: got %q, want %q", result.code, "tail-without-newline")
	}
}

func TestListenForOAuth2CallbackFailsWhenFirstAddressBusy(t *testing.T) {
	busy, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("could not occupy a port: %v", err)
	}
	defer busy.Close()

	listeners, err := listenForOAuth2Callback([]string{busy.Addr().String()})
	if err == nil {
		for _, l := range listeners {
			l.Close()
		}
		t.Fatal("expected error when the first address is busy, got nil")
	}
	if len(listeners) != 0 {
		t.Errorf("expected no listeners, got %d", len(listeners))
	}
}

func TestListenForOAuth2CallbackSkipsBusySecondAddress(t *testing.T) {
	busy, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("could not occupy a port: %v", err)
	}
	defer busy.Close()

	listeners, err := listenForOAuth2Callback([]string{"127.0.0.1:0", busy.Addr().String()})
	defer func() {
		for _, l := range listeners {
			l.Close()
		}
	}()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(listeners) != 1 {
		t.Errorf("listeners: got %d, want 1", len(listeners))
	}
}

// newLoopbackListener returns a listener on a free loopback port.
func newLoopbackListener(t *testing.T) net.Listener {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("could not listen: %v", err)
	}
	return listener
}

// assertListenerClosed fails if a new connection to addr is accepted.
func assertListenerClosed(t *testing.T, addr string) {
	t.Helper()
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get("http://" + addr + "/")
	if err == nil {
		resp.Body.Close()
		t.Fatalf("expected listener at %s to be closed, but request succeeded", addr)
	}
}

// tokenServerRecord counts token endpoint calls and records the code_verifier
// it received.
type tokenServerRecord struct {
	hits      atomic.Int32
	verifiers chan string
}

// newTokenServer returns a token endpoint that expects wantCode with
// authorization_code grant and a non-empty code_verifier.
func newTokenServer(t *testing.T, wantCode string) (*httptest.Server, *tokenServerRecord) {
	t.Helper()
	record := &tokenServerRecord{verifiers: make(chan string, 4)}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		record.hits.Add(1)
		if err := r.ParseForm(); err != nil {
			t.Errorf("token request: could not parse form: %v", err)
			http.Error(w, "bad form", http.StatusBadRequest)
			return
		}
		if got := r.PostForm.Get("grant_type"); got != "authorization_code" {
			t.Errorf("grant_type: got %q, want %q", got, "authorization_code")
		}
		if got := r.PostForm.Get("code"); got != wantCode {
			t.Errorf("code: got %q, want %q", got, wantCode)
		}
		verifier := r.PostForm.Get("code_verifier")
		if verifier == "" {
			t.Error("code_verifier: empty")
		}
		record.verifiers <- verifier
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"access_token":"at","token_type":"bearer","refresh_token":"rt","expires_in":3600}`))
	}))
	t.Cleanup(server.Close)
	return server, record
}

type flowOutcome struct {
	token *oauth2.Token
	err   error
}

// startFlow runs runAuthorizationCodeFlow in a goroutine. Output lines are sent
// to the returned channel.
func startFlow(config *oauth2.Config, listeners []net.Listener, paste io.Reader) (<-chan string, <-chan flowOutcome) {
	lines := make(chan string, 10)
	outcome := make(chan flowOutcome, 1)
	output := func(line string) { lines <- line }
	go func() {
		token, err := runAuthorizationCodeFlow(output, config, true, listeners, paste)
		outcome <- flowOutcome{token: token, err: err}
	}()
	return lines, outcome
}

// readNavigateURL reads the first output line and returns the parsed
// authorization URL it contains.
func readNavigateURL(t *testing.T, lines <-chan string) *url.URL {
	t.Helper()
	select {
	case line := <-lines:
		const prefix = "Navigate to "
		const suffix = " and authenticate"
		if !strings.HasPrefix(line, prefix) || !strings.Contains(line, suffix) {
			t.Fatalf("unexpected first output line: %q", line)
		}
		raw := strings.TrimPrefix(line, prefix)
		raw = raw[:strings.Index(raw, suffix)]
		u, err := url.Parse(raw)
		if err != nil {
			t.Fatalf("could not parse authorization URL %q: %v", raw, err)
		}
		return u
	case <-time.After(testFlowTimeout):
		t.Fatal("timed out waiting for the authorization URL")
		return nil
	}
}

func waitForOutcome(t *testing.T, outcome <-chan flowOutcome) flowOutcome {
	t.Helper()
	select {
	case result := <-outcome:
		return result
	case <-time.After(testFlowTimeout):
		t.Fatal("timed out waiting for runAuthorizationCodeFlow to return")
		return flowOutcome{}
	}
}

func TestRunAuthorizationCodeFlowBrowserRedirect(t *testing.T) {
	tokenServer, record := newTokenServer(t, "browser-code")
	config := testAuthConfig(tokenServer.URL)
	listener := newLoopbackListener(t)
	addr := listener.Addr().String()

	lines, outcome := startFlow(config, []net.Listener{listener}, nil)
	authURL := readNavigateURL(t, lines)
	state := authURL.Query().Get("state")
	challenge := authURL.Query().Get("code_challenge")

	resp, err := http.Get("http://" + addr + "/?" + url.Values{"code": {"browser-code"}, "state": {state}}.Encode())
	if err != nil {
		t.Fatalf("browser redirect request failed: %v", err)
	}
	resp.Body.Close()

	result := waitForOutcome(t, outcome)
	if result.err != nil {
		t.Fatalf("unexpected error: %v", result.err)
	}
	if result.token == nil || result.token.AccessToken != "at" {
		t.Fatalf("token: got %+v, want access token %q", result.token, "at")
	}

	var verifier string
	select {
	case verifier = <-record.verifiers:
	case <-time.After(testFlowTimeout):
		t.Fatal("token endpoint was not called")
	}
	sum := sha256.Sum256([]byte(verifier))
	if want := base64.RawURLEncoding.EncodeToString(sum[:]); want != challenge {
		t.Errorf("code_challenge %q does not match S256 of code_verifier (%q)", challenge, want)
	}

	assertListenerClosed(t, addr)
}

func TestRunAuthorizationCodeFlowPastedCode(t *testing.T) {
	tokenServer, record := newTokenServer(t, "pasted-code")
	config := testAuthConfig(tokenServer.URL)
	listener := newLoopbackListener(t)
	addr := listener.Addr().String()

	lines, outcome := startFlow(config, []net.Listener{listener}, strings.NewReader("pasted-code\n"))
	readNavigateURL(t, lines)

	result := waitForOutcome(t, outcome)
	if result.err != nil {
		t.Fatalf("unexpected error: %v", result.err)
	}
	if result.token == nil || result.token.AccessToken != "at" {
		t.Fatalf("token: got %+v, want access token %q", result.token, "at")
	}
	if hits := record.hits.Load(); hits != 1 {
		t.Errorf("token endpoint hits: got %d, want 1", hits)
	}

	assertListenerClosed(t, addr)
}

func TestRunAuthorizationCodeFlowProviderErrorSkipsTokenExchange(t *testing.T) {
	tokenServer, record := newTokenServer(t, "unused")
	config := testAuthConfig(tokenServer.URL)
	listener := newLoopbackListener(t)
	addr := listener.Addr().String()

	lines, outcome := startFlow(config, []net.Listener{listener}, nil)
	authURL := readNavigateURL(t, lines)
	state := authURL.Query().Get("state")

	resp, err := http.Get("http://" + addr + "/?" + url.Values{"error": {"access_denied"}, "state": {state}}.Encode())
	if err != nil {
		t.Fatalf("browser redirect request failed: %v", err)
	}
	resp.Body.Close()

	result := waitForOutcome(t, outcome)
	if result.err == nil {
		t.Fatalf("expected error from provider, got token %+v", result.token)
	}
	if !strings.Contains(result.err.Error(), "access_denied") {
		t.Errorf("err %q does not contain %q", result.err.Error(), "access_denied")
	}
	if hits := record.hits.Load(); hits != 0 {
		t.Errorf("token endpoint hits: got %d, want 0", hits)
	}

	assertListenerClosed(t, addr)
}

func TestRunAuthorizationCodeFlowOpensBrowser(t *testing.T) {
	opened := make(chan string, 1)
	previous := openBrowser
	openBrowser = func(u string) error {
		opened <- u
		return errors.New("no browser")
	}
	t.Cleanup(func() { openBrowser = previous })

	tokenServer, _ := newTokenServer(t, "browser-code")
	config := testAuthConfig(tokenServer.URL)
	listener := newLoopbackListener(t)
	addr := listener.Addr().String()

	lines, outcome := startFlow(config, []net.Listener{listener}, nil)
	authURL := readNavigateURL(t, lines)

	select {
	case u := <-opened:
		if u != authURL.String() {
			t.Errorf("opened URL: got %q, want the printed URL %q", u, authURL.String())
		}
	case <-time.After(testFlowTimeout):
		t.Fatal("browser was not opened")
	}

	// A failing opener must not end the login: the redirect still completes it.
	state := authURL.Query().Get("state")
	resp, err := http.Get("http://" + addr + "/?" + url.Values{"code": {"browser-code"}, "state": {state}}.Encode())
	if err != nil {
		t.Fatalf("browser redirect request failed: %v", err)
	}
	resp.Body.Close()

	if result := waitForOutcome(t, outcome); result.err != nil {
		t.Fatalf("unexpected error: %v", result.err)
	}
}
