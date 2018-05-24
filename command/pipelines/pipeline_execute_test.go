package pipelines

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/spinnaker/spin/command"
	gate "github.com/spinnaker/spin/gateapi"
)

// TODO(jacobkiefer): This test overlaps heavily with pipeline_save_test.go,
// consider factoring common testing code out.
func TestPipelineExecute_basic(t *testing.T) {
	ts := testGatePipelineExecuteSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret != 0 {
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineExecute_fail(t *testing.T) {
	ts := testGateServerFail()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(testPipelineJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--application", "app", "--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is failure here, internal server error.
		t.Fatalf("Command failed with: %d", ret)
	}
}

func TestPipelineExecute_flags(t *testing.T) {
	ts := testGateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	args := []string{"--gate-endpoint", ts.URL} // Missing pipeline app and name.
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, flags are malformed.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineExecute_missingname(t *testing.T) {
	ts := testGateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(missingNameJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--application", "app", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, name is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

func TestPipelineExecute_missingapp(t *testing.T) {
	ts := testGateServerSuccess()
	defer ts.Close()

	meta := command.ApiMeta{}
	tempFile := tempPipelineFile(missingAppJsonStr)
	if tempFile == nil {
		t.Fatal("Could not create temp pipeline file.")
	}
	defer os.Remove(tempFile.Name())

	args := []string{"--name", "one", "--gate-endpoint", ts.URL}
	cmd := PipelineExecuteCommand{
		ApiMeta: meta,
	}
	ret := cmd.Run(args)
	if ret == 0 { // Success is actually failure, app is missing from spec.
		t.Fatal("Command errantly succeeded.", ret)
	}
}

// testGatePipelineExecuteSuccess spins up a local http server that we will configure the GateClient
// to direct requests to. Responds with a 200 OK.
func testGatePipelineExecuteSuccess() *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// This is an HttpEntity.
		resp := gate.ResponseEntity{StatusCode: "200 OK", StatusCodeValue: 200}
		b, _ := json.Marshal(&resp)
		w.WriteHeader(http.StatusAccepted)
		fmt.Fprintln(w, string(b)) // Just write an empty 200 success on save.
	}))
}
