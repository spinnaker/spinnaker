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
	"bufio"
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/cookiejar"
	_ "net/http/pprof"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"syscall"

	"github.com/pkg/errors"
	"golang.org/x/crypto/ssh/terminal"
	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
	"sigs.k8s.io/yaml"

	"github.com/spinnaker/spin/cmd/output"
	"github.com/spinnaker/spin/config"
	"github.com/spinnaker/spin/config/auth"
	iap "github.com/spinnaker/spin/config/auth/iap"
	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
	"github.com/spinnaker/spin/version"
)

const (
	// defaultConfigFileMode is the default file mode used for config files. This corresponds to
	// the Unix file permissions u=rw,g=,o= so that config files with cached tokens, at least by
	// default, are only readable by the user that owns the config file.
	defaultConfigFileMode os.FileMode = 0600 // u=rw,g=,o=
)

// GatewayClient is the wrapper with authentication
type GatewayClient struct {
	// The exported fields below should be set by anyone using a command
	// with an GatewayClient field. These are expected to be set externally
	// (not from within the command itself).

	// Generate Gate Api client.
	*gate.APIClient

	// Spin CLI configuration.
	Config config.Config

	// Context for OAuth2 access token.
	Context context.Context

	// This is the set of flags global to the command parser.
	gateEndpoint string

	ignoreCertErrors bool

	// Location of the spin config.
	configLocation string

	// Raw Http Client to do OAuth2 login.
	httpClient *http.Client

	ui output.Ui
}

func (m *GatewayClient) GateEndpoint() string {
	if m.Config.Gate.Endpoint == "" && m.gateEndpoint == "" {
		return "http://localhost:8084"
	}
	if m.gateEndpoint != "" {
		return m.gateEndpoint
	}
	return m.Config.Gate.Endpoint
}

// Create new spinnaker gateway client with flag
func NewGateClient(ui output.Ui, gateEndpoint, defaultHeaders, configLocation string, ignoreCertErrors bool) (*GatewayClient, error) {
	gateClient := &GatewayClient{
		gateEndpoint:     gateEndpoint,
		ignoreCertErrors: ignoreCertErrors,
		ui:               ui,
		Context:          context.Background(),
	}

	err := userConfig(gateClient, configLocation)
	if err != nil {
		return nil, err
	}

	// Api client initialization.
	httpClient, err := InitializeHTTPClient(gateClient.Config.Auth)
	if err != nil {
		ui.Error("Could not initialize http client, failing.")
		return nil, unwrapErr(ui, err)
	}

	gateClient.Context, err = ContextWithAuth(gateClient.Context, gateClient.Config.Auth)

	if ignoreCertErrors {
		if httpClient.Transport.(*http.Transport).TLSClientConfig == nil {
			httpClient.Transport.(*http.Transport).TLSClientConfig = &tls.Config{
				InsecureSkipVerify: true,
			}
		} else {
			httpClient.Transport.(*http.Transport).TLSClientConfig.InsecureSkipVerify = true
		}
	}

	gateClient.httpClient = httpClient
	updatedConfig := false
	updatedMessage := ""

	if gateClient.Config.Auth != nil && gateClient.Config.Auth.OAuth2 != nil {
		updatedConfig, err = authenticateOAuth2(ui.Output, httpClient, gateClient.GateEndpoint(), gateClient.Config.Auth)
		if err != nil {
			ui.Error(fmt.Sprintf("OAuth2 Authentication failed: %v", err))
			return nil, unwrapErr(ui, err)
		}

		updatedMessage = "Caching oauth2 token."
	}

	if gateClient.Config.Auth != nil && gateClient.Config.Auth.GoogleServiceAccount != nil {
		updatedConfig, err = authenticateGoogleServiceAccount(httpClient, gateClient.GateEndpoint(), gateClient.Config.Auth)
		if err != nil {
			ui.Error(fmt.Sprintf("Google service account authentication failed: %v", err))
			return nil, unwrapErr(ui, err)
		}
		updatedMessage = "Caching gsa token."
	}

	if updatedConfig {
		ui.Info(updatedMessage)
		_ = gateClient.writeYAMLConfig()
	}

	if gateClient.Config.Auth != nil && gateClient.Config.Auth.Ldap != nil {
		if err = authenticateLdap(ui.Output, httpClient, gateClient.GateEndpoint(), gateClient.Config.Auth); err != nil {
			ui.Error(fmt.Sprintf("LDAP Authentication failed: %v", err))
			return nil, unwrapErr(ui, err)
		}
	}

	m := make(map[string]string)

	if defaultHeaders != "" {
		headers := strings.Split(defaultHeaders, ",")
		for _, element := range headers {
			header := strings.SplitN(element, "=", 2)
			if len(header) != 2 {
				return nil, fmt.Errorf("Bad default-header value, use key=value form: %s", element)
			}
			m[strings.TrimSpace(header[0])] = strings.TrimSpace(header[1])
		}
	}

	cfg := &gate.Configuration{
		BasePath:      gateClient.GateEndpoint(),
		DefaultHeader: m,
		UserAgent:     fmt.Sprintf("%s/%s", version.UserAgent, version.String()),
		HTTPClient:    httpClient,
	}
	gateClient.APIClient = gate.NewAPIClient(cfg)

	// TODO: Verify version compatibility between Spin CLI and Gate.
	_, _, err = gateClient.VersionControllerApi.GetVersionUsingGET(gateClient.Context)
	if err != nil {
		ui.Error("Could not reach Gate, please ensure it is running. Failing.")
		return nil, err
	}

	return gateClient, nil
}

// unwrapErr will convert any errors made with `errors.Wrap` into ui.Error calls
// and return the wrapped error. This allows for some error handling inside
// functions that do not have access to a `ui` object.
func unwrapErr(ui output.Ui, err error) error {
	if e := errors.Unwrap(err); e != nil {
		ui.Error(e.Error())
		return e
	}
	return err
}

func userConfig(gateClient *GatewayClient, configLocation string) error {
	if configLocation != "" {
		gateClient.configLocation = configLocation
	} else {
		userHome, err := os.UserHomeDir()
		if err != nil {
			gateClient.ui.Error("Could not read current user home directory from environment, failing.")
			return err
		}
		gateClient.configLocation = filepath.Join(userHome, ".spin", "config")
	}

	yamlFile, err := ioutil.ReadFile(gateClient.configLocation)
	// Please note that https://github.com/spinnaker/spin/pull/243 introduced better coding standards and
	// as a result, your auth config needs to match the struct tags through all the config structs
	// e.g. the struct tags for oauth in the config are set in the local oauth package here
	// but unmarshal to an upstream oauth package, so the cached token needs to match
	// https://godoc.org/golang.org/x/oauth2#Token
	if yamlFile != nil {
		err = yaml.UnmarshalStrict([]byte(os.ExpandEnv(string(yamlFile))), &gateClient.Config)
		if err != nil {
			gateClient.ui.Error(fmt.Sprintf("Could not deserialize config file with contents: %s, failing.", yamlFile))
			return err
		}
	} else {
		gateClient.Config = config.Config{}
	}
	return nil
}

// InitializeHTTPClient will return an *http.Client configured with
// optional TLS keys as specified in the auth.Config
func InitializeHTTPClient(auth *auth.Config) (*http.Client, error) {
	cookieJar, _ := cookiejar.New(nil)
	client := http.Client{
		Jar:       cookieJar,
		Transport: http.DefaultTransport.(*http.Transport).Clone(),
	}

	if auth == nil || !auth.Enabled || auth.X509 == nil {
		return &client, nil
	}

	X509 := auth.X509
	client.Transport.(*http.Transport).TLSClientConfig = &tls.Config{
		InsecureSkipVerify: auth.IgnoreCertErrors,
	}

	if !X509.IsValid() {
		// Misconfigured.
		return nil, errors.New("Incorrect x509 auth configuration.\nMust specify certPath/keyPath or cert/key pair.")
	}

	if X509.CertPath != "" && X509.KeyPath != "" {
		certPath, err := util.ExpandHomeDir(X509.CertPath)
		if err != nil {
			return nil, err
		}
		keyPath, err := util.ExpandHomeDir(X509.KeyPath)
		if err != nil {
			return nil, err
		}

		cert, err := tls.LoadX509KeyPair(certPath, keyPath)
		if err != nil {
			return nil, err
		}

		clientCA, err := ioutil.ReadFile(certPath)
		if err != nil {
			return nil, err
		}

		return initializeX509Config(client, clientCA, cert), nil
	}

	if X509.Cert != "" && X509.Key != "" {
		certBytes := []byte(X509.Cert)
		keyBytes := []byte(X509.Key)
		cert, err := tls.X509KeyPair(certBytes, keyBytes)
		if err != nil {
			return nil, err
		}

		return initializeX509Config(client, certBytes, cert), nil
	}

	// Misconfigured.
	return nil, errors.New("Incorrect x509 auth configuration.\nMust specify certPath/keyPath or cert/key pair.")
}

// Authenticate is helper function to attempt to authenticate with OAuth2,
// Google Service Account or LDAP as configured in the auth.Config.
func Authenticate(output func(string), httpClient *http.Client, endpoint string, auth *auth.Config) (updatedConfig bool, err error) {
	updatedConfig, err = authenticateOAuth2(output, httpClient, endpoint, auth)
	if updatedConfig || err != nil {
		return updatedConfig, err
	}

	updatedConfig, err = authenticateGoogleServiceAccount(httpClient, endpoint, auth)
	if updatedConfig || err != nil {
		return updatedConfig, err
	}

	if err = authenticateLdap(output, httpClient, endpoint, auth); err != nil {
		return false, err
	}
	return false, nil
}

// ContextWithAuth will set context variables that maybe necessary for IAP or Basic
// authentication per-request.  This can be used in conjunction with AddAuthHeaders
// to ensure auth headers from the context are added to all requests.
func ContextWithAuth(ctx context.Context, auth *auth.Config) (context.Context, error) {
	if auth != nil && auth.Enabled && auth.Iap != nil {
		accessToken, err := authenticateIAP(auth)
		ctx = context.WithValue(ctx, gate.ContextAccessToken, accessToken)
		return ctx, err
	} else if auth != nil && auth.Enabled && auth.Basic != nil {
		if !auth.Basic.IsValid() {
			return nil, errors.New("Incorrect Basic auth configuration. Must include username and password.")
		}
		ctx = context.WithValue(ctx, gate.ContextBasicAuth, gate.BasicAuth{
			UserName: auth.Basic.Username,
			Password: auth.Basic.Password,
		})
		return ctx, nil
	}
	return ctx, nil
}

// AddAuthHeaders will use the context variables to set via ContextWithAuth
// to add any necessary authentication headers to the request.
func AddAuthHeaders(ctx context.Context, req *http.Request) error {
	if ctx != nil {
		return nil
	}

	// add context to the request
	req = req.WithContext(ctx)

	// Walk through any authentication.

	// OAuth2 authentication
	if tok, ok := ctx.Value(gate.ContextOAuth2).(oauth2.TokenSource); ok {
		// We were able to grab an oauth2 token from the context
		latestToken, err := tok.Token()
		if err != nil {
			return err
		}
		latestToken.SetAuthHeader(req)
	}

	// Basic HTTP Authentication
	if auth, ok := ctx.Value(gate.ContextBasicAuth).(gate.BasicAuth); ok {
		req.SetBasicAuth(auth.UserName, auth.Password)
	}

	// AccessToken Authentication
	if auth, ok := ctx.Value(gate.ContextAccessToken).(string); ok {
		req.Header.Add("Authorization", "Bearer "+auth)
	}
	return nil
}

func initializeX509Config(client http.Client, clientCA []byte, cert tls.Certificate) *http.Client {
	clientCertPool := x509.NewCertPool()
	clientCertPool.AppendCertsFromPEM(clientCA)

	client.Transport.(*http.Transport).TLSClientConfig.MinVersion = tls.VersionTLS12
	client.Transport.(*http.Transport).TLSClientConfig.PreferServerCipherSuites = true
	client.Transport.(*http.Transport).TLSClientConfig.Certificates = []tls.Certificate{cert}
	return &client
}

func authenticateOAuth2(output func(string), httpClient *http.Client, endpoint string, auth *auth.Config) (configUpdated bool, err error) {
	if auth != nil && auth.Enabled && auth.OAuth2 != nil {
		OAuth2 := auth.OAuth2
		if !OAuth2.IsValid() {
			// TODO(jacobkiefer): Improve this error message.
			return false, errors.New("incorrect OAuth2 auth configuration")
		}

		config := &oauth2.Config{
			ClientID:     OAuth2.ClientId,
			ClientSecret: OAuth2.ClientSecret,
			RedirectURL:  "http://localhost:8085",
			Scopes:       OAuth2.Scopes,
			Endpoint: oauth2.Endpoint{
				AuthURL:  OAuth2.AuthUrl,
				TokenURL: OAuth2.TokenUrl,
			},
		}
		var newToken *oauth2.Token

		if auth.OAuth2.CachedToken != nil {
			// Look up cached credentials to save oauth2 roundtrip.
			token := auth.OAuth2.CachedToken
			tokenSource := config.TokenSource(context.Background(), token)
			newToken, err = tokenSource.Token()
			if err != nil {
				return false, errors.Wrapf(err, "Could not refresh token from source: %v", tokenSource)
			}
		} else {
			// Do roundtrip.
			http.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				code := r.FormValue("code")
				fmt.Fprintln(w, code)
			}))
			go http.ListenAndServe(":8085", nil)
			// Note: leaving server connection open for scope of request, will be reaped on exit.

			verifier, verifierCode, err := generateCodeVerifier()
			if err != nil {
				return false, err
			}

			codeVerifier := oauth2.SetAuthURLParam("code_verifier", verifier)
			codeChallenge := oauth2.SetAuthURLParam("code_challenge", verifierCode)
			challengeMethod := oauth2.SetAuthURLParam("code_challenge_method", "S256")

			authURL := config.AuthCodeURL("state-token", oauth2.AccessTypeOffline, oauth2.ApprovalForce, challengeMethod, codeChallenge)
			output(fmt.Sprintf("Navigate to %s and authenticate", authURL))
			code := prompt(output, "Paste authorization code:")

			newToken, err = config.Exchange(context.Background(), code, codeVerifier)
			if err != nil {
				return false, err
			}
		}
		OAuth2.CachedToken = newToken
		err = login(httpClient, endpoint, newToken.AccessToken)
		if err != nil {
			return false, err
		}
		return true, nil
	}
	return false, nil
}

func authenticateIAP(auth *auth.Config) (string, error) {
	iapConfig := auth.Iap
	token, err := iap.GetIapToken(*iapConfig)
	return token, err
}

func authenticateGoogleServiceAccount(httpClient *http.Client, endpoint string, auth *auth.Config) (updatedConfig bool, err error) {
	if auth == nil {
		return false, nil
	}

	gsa := auth.GoogleServiceAccount
	if !gsa.IsEnabled() {
		return false, nil
	}

	if gsa.CachedToken != nil && gsa.CachedToken.Valid() {
		return false, login(httpClient, endpoint, gsa.CachedToken.AccessToken)
	}
	gsa.CachedToken = nil

	var source oauth2.TokenSource
	if gsa.File == "" {
		source, err = google.DefaultTokenSource(context.Background(), "profile", "email")
	} else {
		serviceAccountJSON, ferr := ioutil.ReadFile(gsa.File)
		if ferr != nil {
			return false, ferr
		}
		source, err = google.JWTAccessTokenSourceFromJSON(serviceAccountJSON, "https://accounts.google.com/o/oauth2/v2/auth")
	}
	if err != nil {
		return false, err
	}

	token, err := source.Token()
	if err != nil {
		return false, err
	}

	if err := login(httpClient, endpoint, token.AccessToken); err != nil {
		return false, err
	}

	gsa.CachedToken = token
	return true, nil
}

func login(httpClient *http.Client, endpoint string, accessToken string) error {
	loginReq, err := http.NewRequest("GET", endpoint+"/login", nil)
	if err != nil {
		return err
	}
	loginReq.Header.Set("Authorization", fmt.Sprintf("Bearer %s", accessToken))

	_, err = httpClient.Do(loginReq) // Login to establish session.
	if err != nil {
		return errors.New("login failed")
	}
	return nil
}

func authenticateLdap(output func(string), httpClient *http.Client, endpoint string, auth *auth.Config) error {
	if auth != nil && auth.Enabled && auth.Ldap != nil {
		if auth.Ldap.Username == "" {
			auth.Ldap.Username = prompt(output, "Username:")
		}

		if auth.Ldap.Password == "" {
			auth.Ldap.Password = securePrompt(output, "Password:")
		}

		if !auth.Ldap.IsValid() {
			return errors.New("Incorrect LDAP auth configuration. Must include username and password.")
		}

		form := url.Values{}
		form.Add("username", auth.Ldap.Username)
		form.Add("password", auth.Ldap.Password)

		loginReq, err := http.NewRequest("POST", endpoint+"/login", strings.NewReader(form.Encode()))
		loginReq.Header.Set("Content-Type", "application/x-www-form-urlencoded")
		if err != nil {
			return err
		}

		_, err = httpClient.Do(loginReq) // Login to establish session.

		if err != nil {
			return errors.New("ldap authentication failed")
		}
	}

	return nil
}

// writeYAMLConfig writes an updated YAML configuration file to the receiver's config file location.
// It returns an error, but the error may be ignored.
func (m *GatewayClient) writeYAMLConfig() error {
	// Write updated config file with u=rw,g=,o= permissions by default.
	// The default permissions should only be used if the file no longer exists.
	err := writeYAML(&m.Config, m.configLocation, defaultConfigFileMode)
	if err != nil {
		m.ui.Warn(fmt.Sprintf("Error caching oauth2 token: %v", err))
	}
	return err
}

func writeYAML(v interface{}, dest string, defaultMode os.FileMode) error {
	// Write config with cached token
	buf, err := yaml.Marshal(v)
	if err != nil {
		return err
	}

	mode := defaultMode
	info, err := os.Stat(dest)
	if err != nil && !os.IsNotExist(err) {
		return nil
	} else {
		// Preserve existing file mode
		mode = info.Mode()
	}

	return ioutil.WriteFile(dest, buf, mode)
}

// generateCodeVerifier generates an OAuth2 code verifier
// in accordance to https://www.oauth.com/oauth2-servers/pkce/authorization-request and
// https://tools.ietf.org/html/rfc7636#section-4.1.
func generateCodeVerifier() (verifier string, code string, err error) {
	randomBytes := make([]byte, 64)
	if _, err := rand.Read(randomBytes); err != nil {
		return "", "", errors.Wrap(err, "Could not generate random string for code_verifier")
	}
	verifier = base64.RawURLEncoding.EncodeToString(randomBytes)
	verifierHash := sha256.Sum256([]byte(verifier))
	code = base64.RawURLEncoding.EncodeToString(verifierHash[:]) // Slice for type conversion
	return verifier, code, nil
}

func prompt(output func(string), inputMsg string) string {
	reader := bufio.NewReader(os.Stdin)
	output(inputMsg)
	text, _ := reader.ReadString('\n')
	return strings.TrimSpace(text)
}

func securePrompt(output func(string), inputMsg string) string {
	output(inputMsg)
	byteSecret, _ := terminal.ReadPassword(int(syscall.Stdin))
	secret := string(byteSecret)
	return strings.TrimSpace(secret)
}
