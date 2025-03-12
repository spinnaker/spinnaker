package config

import (
	"testing"

	"sigs.k8s.io/yaml"
)

func TestBasicMarshalling(t *testing.T) {
	var cfg Config

	cfg.Gate.Endpoint = "test"

	byteSlice, err := yaml.Marshal(cfg)

	if err != nil {
		t.Errorf("Unexpected error marshalling YAML: %v", err)
	}

	got := string(byteSlice)
	want := "auth: null\ngate:\n  endpoint: test\n"

	if got != want {
		t.Errorf("Unexpected marshalled YAML, saw '%s', want '%s'", got, want)
	}
}

func TestYAMLRoundTrip(t *testing.T) {
	var cfg Config

	want := "auth:\n  enabled: false\n  ignoreCertErrors: true\n  ignoreRedirects: true\ngate:\n  endpoint: test\n  retryTimeout: 900\n"
	err := yaml.Unmarshal([]byte(want), &cfg)

	if err != nil {
		t.Errorf("Unexpected unmarshalling error: %v", err)
	}

	byteSlice, err := yaml.Marshal(cfg)
	if err != nil {
		t.Errorf("Unexpected marshalling error: %v", err)
	}

	got := string(byteSlice)
	if got != want {
		t.Errorf("Round-tripped YAML does not match: saw '%s', want '%s'", got, want)
	}
}
