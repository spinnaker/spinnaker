package util

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/andreyvit/diff"
)

func TestGateMuxWithVersionHandler() *http.ServeMux {
	mux := http.NewServeMux()
	mux.Handle("/version", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"version": "Unknown",
		}
		b, _ := json.Marshal(&payload)
		w.Header().Add("content-type", "application/json")
		fmt.Fprintln(w, string(b))
	}))

	return mux
}

// TestPrettyJsonDiff compares prettified json against an expected json string.
// Leading and trailing whitespace is trimmed before the comparison.
func TestPrettyJsonDiff(t *testing.T, description, expected string, recieved []byte) {
	pretty := new(bytes.Buffer)
	err := json.Indent(pretty, recieved, "", " ")
	if err != nil {
		t.Fatalf("Failed to pretify %s: %v\n%s", description, err, string(recieved))
	}
	recievedPretty := strings.TrimSpace(pretty.String())
	if expected != recievedPretty {
		t.Fatalf("Unexpected %s:\n%s", description, diff.LineDiff(expected, recievedPretty))
	}
}

// NewTestBufferHandlerFunc returns an http.Handler which buffers the request
// body, writes the response header, and writes the response body. Requests
// that do not match the specified method recieve 404 Not Found.
func NewTestBufferHandlerFunc(method string, requestBuffer io.Writer, responseHeader int, responseBody string) http.Handler {
	buffer := requestBuffer
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != method {
			w.WriteHeader(http.StatusNotFound)
			return
		}

		defer r.Body.Close()
		_, err := io.Copy(buffer, r.Body)
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed to copy body to buffer: %v", err), http.StatusInternalServerError)
			return
		}

		// Empty response body. Status: 200 Success
		w.Header().Add("content-type", "application/json")
		w.WriteHeader(responseHeader)
		fmt.Fprintln(w, responseBody)
	})
}
