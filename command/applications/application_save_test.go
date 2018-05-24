package applications

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spinnaker/spin/command"
)

const (
	NAME  = "app"
	EMAIL = "appowner@spinnaker-test.net"
)

func TestApplicationSave_basic(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationSave_fail(t *testing.T) {
	ts := GateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestApplicationSave_flags(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, flags are malformed.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestApplicationSave_missingname(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--owner-email", EMAIL,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, name is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestApplicationSave_missingemail(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--cloud-providers", "gce,kubernetes",
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, id missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestApplicationSave_missingproviders(t *testing.T) {
	ts := GateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{
		"--application-name", NAME,
		"--owner-email", EMAIL,
		"--gate-endpoint", ts.URL,
	}
	cmd := ApplicationSaveCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, app is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

// GateServerSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 OK.
func GateServerSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"ref": "/tasks/somethingtotallyreasonable",
		}
		b, _ := json.Marshal(&payload)
		fmt.Fprintln(w, string(b)) // Just write an empty 200 success on save.
	}))
}

// GateServerFail spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 500 InternalServerError.
func GateServerFail() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// TODO(jacobkiefer): Mock more robust errors once implemented upstream.
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
	}))
}
