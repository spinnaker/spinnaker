package main

import (
	"github.com/mitchellh/cli"

	"github.com/spinnaker/spin/command"
)

var Commands map[string]cli.CommandFactory

func init() {
	meta := command.ApiMeta{}

	Commands = map[string]cli.CommandFactory{
		"pipeline save": func() (cli.Command, error) {
			return &command.PipelineSaveCommand{
				ApiMeta: meta,
			}, nil
		},
	}
}
