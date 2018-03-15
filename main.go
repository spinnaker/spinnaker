package main

import (
	"fmt"
	"os"

	"github.com/mitchellh/cli"
)

func main() {
	args := os.Args[1:]

	cli := &cli.CLI{
		Name:     "spin",
		Args:     args,
		Commands: Commands,
	}

	c, err := cli.Run()

	if err != nil {
		fmt.Fprintf(os.Stderr, "Error executing CLI: %s\n", err)
		c = 1
	}

	os.Exit(c)
}
