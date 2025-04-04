package main

import (
	"fmt"
	"io/ioutil"
	"net/http"
	"text/template"
)

var env string = ""
var config string = ""

func update_config() error {
	b, err := ioutil.ReadFile("/opt/demo/env/env.yaml")
	if err != nil {
		return err
	}

	env = string(b)

	b, err = ioutil.ReadFile("/opt/demo/config/config.yaml")
	if err != nil {
		return err
	}

	config = string(b)

	return nil
}

func index(w http.ResponseWriter, r *http.Request) {
	fmt.Printf("Handling %+v\n", r)

	uerr := update_config()
	if uerr != nil {
		http.Error(w, fmt.Sprintf("Error reading config: %v", uerr), 500)
		return
	}

	t, terr := template.ParseFiles("/content/index.html")

	if terr != nil {
		http.Error(w, fmt.Sprintf("Error loading template: %v", terr), 500)
		return
	}

	config := map[string]string{
		"Config": config,
		"Env": env,
	}

	t.Execute(w, config)
}

func main() {
	http.HandleFunc("/", index)

	port := ":80"
	fmt.Printf("Starting to service on port %s\n", port)
	http.ListenAndServe(port, nil)
}
