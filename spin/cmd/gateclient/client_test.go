package gateclient

import (
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/spinnaker/spin/cmd/output"
	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

func TestNewGateClientSetsContextAccessTokenFromOAuth2CachedToken(t *testing.T) {
	token := "cached-access-token"
	loginHeaders := make(chan string, 1)

	mux := util.TestGateMuxWithVersionHandler()
	mux.Handle("/login/oauth2/code/test-provider", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		loginHeaders <- r.Header.Get("Authorization")
		w.WriteHeader(http.StatusOK)
	}))

	testServer := httptest.NewServer(mux)
	defer testServer.Close()

	configPath := filepath.Join(t.TempDir(), "config")
	configContents := "gate:\n" +
		"  endpoint: " + testServer.URL + "\n" +
		"auth:\n" +
		"  enabled: true\n" +
		"  oauth2:\n" +
		"    authUrl: " + testServer.URL + "/oauth/authorize\n" +
		"    tokenUrl: " + testServer.URL + "/oauth/token\n" +
		"    scopes:\n" +
		"      - openid\n" +
		"    provider: test-provider\n" +
		"    cachedToken:\n" +
		"      access_token: " + token + "\n" +
		"      token_type: Bearer\n" +
		"      expiry: " + time.Now().Add(time.Hour).UTC().Format(time.RFC3339) + "\n"

	if err := os.WriteFile(configPath, []byte(configContents), 0o600); err != nil {
		t.Fatalf("failed writing test config: %v", err)
	}

	ui := output.NewUI(true, false, output.MarshalToJson, io.Discard, io.Discard)
	client, err := NewGateClient(ui, "", "", configPath, false, false, 0)
	if err != nil {
		t.Fatalf("NewGateClient returned error: %v", err)
	}

	accessToken, ok := client.Context.Value(gate.ContextAccessToken).(string)
	if !ok {
		t.Fatal("expected gate.ContextAccessToken value in client context")
	}
	if accessToken != token {
		t.Fatalf("unexpected context access token: got %q, want %q", accessToken, token)
	}
}
