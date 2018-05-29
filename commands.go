package main

import (
	"github.com/mitchellh/cli"
	"github.com/spinnaker/spin/command"
	"github.com/spinnaker/spin/command/applications"
	"github.com/spinnaker/spin/command/pipelines"
)

var Commands map[string]cli.CommandFactory

func init() {
	meta := command.ApiMeta{}

	Commands = map[string]cli.CommandFactory{
		"application list": func() (cli.Command, error) {
			return &applications.ApplicationListCommand{
				ApiMeta: meta,
			}, nil
		},
		"application save": func() (cli.Command, error) {
			return &applications.ApplicationSaveCommand{
				ApiMeta: meta,
			}, nil
		},
		"pipeline execute": func() (cli.Command, error) {
			return &pipelines.PipelineExecuteCommand{
				ApiMeta: meta,
			}, nil
		},
		"pipeline get": func() (cli.Command, error) {
			return &pipelines.PipelineGetCommand{
				ApiMeta: meta,
			}, nil
		},
		"pipeline list": func() (cli.Command, error) {
			return &pipelines.PipelineListCommand{
				ApiMeta: meta,
			}, nil
		},
		"pipeline save": func() (cli.Command, error) {
			return &pipelines.PipelineSaveCommand{
				ApiMeta: meta,
			}, nil
		},
	}
}
