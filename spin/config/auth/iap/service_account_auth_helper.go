// Copyright (c) 2019, Google Inc.
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
	"crypto/rsa"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
	"time"

	"golang.org/x/oauth2/jws"

	oauth "golang.org/x/oauth2/google"
)

const (
	audURL                  = "https://www.googleapis.com/oauth2/v4/token"
	jwtBearerTokenGrantType = "urn:ietf:params:oauth:grant-type:jwt-bearer"
	expirationSecs          = 3600
)

// GetIDTokenWithServiceAccount gets an IAP ID Token required for authenticating a service account with IAP.
// For more info, see https://cloud.google.com/iap/docs/authentication-howto#authenticating_from_a_service_account
func GetIDTokenWithServiceAccount(config Config) (string, error) {
	creds, err := ioutil.ReadFile(config.ServiceAccountKeyPath)
	if err != nil {
		return "", fmt.Errorf("could not read service account creds file: %v", err)
	}

	// Creating and signing JWT token.

	jwtc, err := oauth.JWTConfigFromJSON(creds)
	if err != nil {
		return "", fmt.Errorf("could not parse config from service account creds: %v", err)
	}

	pk, err := parseKey(jwtc.PrivateKey)
	if err != nil {
		return "", fmt.Errorf("invalid private json key: %v", err)
	}

	now := time.Now().Unix()

	claims := &jws.ClaimSet{
		Aud: audURL,
		Iss: jwtc.Email,
		Sub: jwtc.Email,
		Iat: now,
		Exp: now + expirationSecs,
		PrivateClaims: map[string]interface{}{
			"target_audience": config.IapClientId,
		},
	}

	h := &jws.Header{
		Algorithm: "RS256",
		Typ:       "JWT",
		KeyID:     jwtc.PrivateKeyID,
	}

	jwt, err := jws.Encode(h, claims, pk)
	if err != nil {
		return "", fmt.Errorf("failed to encode jwt: %v", err)
	}

	// Use the JWT to get the OIDC token.

	form := url.Values{}
	form.Add("grant_type", jwtBearerTokenGrantType)
	form.Add("assertion", jwt)

	req, err := http.NewRequest("POST", audURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", fmt.Errorf("failed to create request: %v", err)
	}
	req.Header.Add("Content-Type", "application/x-www-form-urlencoded")

	client := &http.Client{}

	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("failed to get OIDC token: %s", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("failed to get successful response: %+v", resp)
	}

	tokenBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("could not read response body of OIDC token request")
	}

	var payload map[string]interface{}
	err = json.Unmarshal(tokenBody, &payload)
	if err != nil {
		return "", fmt.Errorf("failed to parse access token payload %q: %v", string(tokenBody), err)
	}

	return fmt.Sprintf("%v", payload["id_token"]), nil
}

func parseKey(key []byte) (*rsa.PrivateKey, error) {
	block, _ := pem.Decode(key)
	if block != nil {
		key = block.Bytes
	}
	parsedKey, err := x509.ParsePKCS8PrivateKey(key)
	if err != nil {
		parsedKey, err = x509.ParsePKCS1PrivateKey(key)
		if err != nil {
			return nil, fmt.Errorf("private key should be a PEM or plain PKSC1 or PKCS8; parse error: %v", err)
		}
	}
	parsed, ok := parsedKey.(*rsa.PrivateKey)
	if !ok {
		return nil, errors.New("private key is not an RSA key")
	}
	return parsed, nil
}
