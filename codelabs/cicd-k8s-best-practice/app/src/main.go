package main

import (
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"text/template"
)

func index(w http.ResponseWriter, r *http.Request) {
	fmt.Printf("Handling %+v\n", r)

	b, rerr := ioutil.ReadFile("/opt/demo/config.yaml")
	if rerr != nil {
		http.Error(w, fmt.Sprintf("Error reading config: %v", rerr), 500)
		return
	}

	t, terr := template.ParseFiles("/content/index.html")

	if terr != nil {
		http.Error(w, fmt.Sprintf("Error loading template: %v", terr), 500)
		return
	}

	config := map[string]string{
		"Message": string(b),
		"Feature": os.Getenv("FEATURE"),
	}

	t.Execute(w, config)
}

func main() {
	http.HandleFunc("/", index)

	port := ":80"
	fmt.Printf("Starting to service on port %s\n", port)
	http.ListenAndServe(port, nil)
}
