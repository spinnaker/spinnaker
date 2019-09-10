package util

import (
	"encoding/json"
	"fmt"
	"net/http"
)

func TestGateMuxWithVersionHandler() *http.ServeMux {
	mux := http.NewServeMux()
	mux.Handle("/version", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		payload := map[string]string{
			"version": "Unknown",
		}
		b, _ := json.Marshal(&payload)
		fmt.Fprintln(w, string(b))
	}))

	return mux
}
