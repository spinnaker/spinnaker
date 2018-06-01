// Copyright (c) 2018, Google, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

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
		"application get": func() (cli.Command, error) {
			return &applications.ApplicationGetCommand{
				ApiMeta: meta,
			}, nil
		},
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
