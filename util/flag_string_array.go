package util

import (
	"strings"
)

// FlagStringArray implements an array of strings to be used in flags.
// Flags of this type should have comma-delimited strings as the value.
type FlagStringArray []string

func (f *FlagStringArray) String() string {
	return strings.Join(*f, ",")
}

func (f *FlagStringArray) Set(value string) error {
	stripped := make([]string, 0)
	unstripped := strings.Split(value, ",")
	for _, us := range unstripped {
		st := strings.TrimSpace(us)
		stripped = append(stripped, st)
	}
	*f = stripped
	return nil
}
