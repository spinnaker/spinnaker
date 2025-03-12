package util

import (
	"os"
	"strings"
)

// ExpandHomeDir expands filepath with a tilde prefix. If the given filepath
// doesn't have a tilde this function return the filepath as it is.
func ExpandHomeDir(path string) (string, error) {
	if !strings.HasPrefix(path, "~") {
		return path, nil
	}

	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return home + strings.TrimPrefix(path, "~"), nil
}
