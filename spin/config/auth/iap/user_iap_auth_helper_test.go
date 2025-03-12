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
	"testing"
)

const (
	expectedOauthUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=testClientId&redirect_uri=http%3A%2F%2Flocalhost%3A80&response_type=code&scope=openid+email&state=testClientState"
)

func TestGetCorrectOauthUrlSuccessful(t *testing.T) {
	clientId := "testClientId"
	clientState := "testClientState"
	port := 80
	oauthUrl := oauthURL(clientId, clientState, port)
	if oauthUrl != expectedOauthUrl {
		t.Errorf("Invalid oauth url, \n Expected:\t%s \n Got:\t%s", expectedOauthUrl, oauthUrl)
	}
}
