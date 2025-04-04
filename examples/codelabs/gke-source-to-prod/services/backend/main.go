package main

import (
	"io"
	"fmt"
	"net/http"
	"os"
)

var count = 1

func index(w http.ResponseWriter, r *http.Request) {
	fmt.Printf("Handling %+v\n", r);

	host, err := os.Hostname()

	if err != nil {
		http.Error(w, fmt.Sprintf("Error retrieving hostname: %v", err), 500)
		return
	}

	msg := fmt.Sprintf("Host: %s\nSuccessful requests: %d", host, count)
	count += 1

	io.WriteString(w, msg)
}

func main() {
	http.HandleFunc("/", index)
	port := ":8000"
	fmt.Printf("Starting to service on port %s\n", port);
	http.ListenAndServe(port, nil)
}
