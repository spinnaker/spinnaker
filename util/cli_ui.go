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

package util

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/mitchellh/cli"
	"github.com/mitchellh/colorstring"
	"github.com/spinnaker/spin/cmd/output"
	"k8s.io/client-go/util/jsonpath"
)

type ColorizeUi struct {
	Colorize     *colorstring.Colorize
	OutputColor  string
	InfoColor    string
	ErrorColor   string
	WarnColor    string
	Ui           cli.Ui
	Quiet        bool
	OutputFormat *output.OutputFormat
}

var UI *ColorizeUi
var hasColor bool

func InitUI(quiet, color bool, outputFormat string) {
	hasColor = color
	UI = &ColorizeUi{
		Colorize:   Colorize(),
		ErrorColor: "[red]",
		WarnColor:  "[yellow]",
		InfoColor:  "[blue]",
		Ui: &cli.BasicUi{
			Writer:      os.Stdout,
			ErrorWriter: os.Stderr,
		},
		Quiet: quiet,
	}
	var err error
	UI.OutputFormat, err = output.ParseOutputFormat(outputFormat)
	if err != nil {
		panic(err)
	}
}

// Colorize initializes the ui colorization.
func Colorize() *colorstring.Colorize {
	return &colorstring.Colorize{
		Colors:  colorstring.DefaultColors,
		Disable: !hasColor,
		Reset:   true,
	}
}

func (u *ColorizeUi) Ask(query string) (string, error) {
	return u.Ui.Ask(u.colorize(query, u.OutputColor))
}

func (u *ColorizeUi) AskSecret(query string) (string, error) {
	return u.Ui.AskSecret(u.colorize(query, u.OutputColor))
}

func (u *ColorizeUi) Output(message string) {
	u.Ui.Output(u.colorize(message, u.OutputColor))
}

// JsonOutput pretty prints the data specified in the input.
// Callers can optionally supply a jsonpath template to pull out nested data in input.
// This leverages the kubernetes jsonpath libs (https://kubernetes.io/docs/reference/kubectl/jsonpath/).
func (u *ColorizeUi) JsonOutput(input interface{}, outputFormat *output.OutputFormat) {
	if outputFormat == nil {
		prettyStr, _ := json.MarshalIndent(input, "", " ")
		u.Output(u.colorize(string(prettyStr), u.OutputColor))
		return
	}

	template := outputFormat.JsonPath
	if template == "" {
		prettyStr, _ := json.MarshalIndent(input, "", " ")
		u.Output(u.colorize(string(prettyStr), u.OutputColor))
	} else {
		jsonValue, err := u.parseJsonPath(input, template)
		if err != nil {
			u.Error(fmt.Sprintf("%v", err))
		}

		// unquote since go quotes the string if the bytes is a string.
		u.Output(u.colorize(u.unquote(jsonValue.String()), u.OutputColor))
	}
}

func (u *ColorizeUi) unquote(input string) string {
	input = strings.TrimPrefix(input, "\"")
	input = strings.TrimSuffix(input, "\"")
	return input
}

// parseJsonPath finds the values specified in the input data as specified with the template.
// This leverages the kubernetes jsonpath libs (https://kubernetes.io/docs/reference/kubectl/jsonpath/).
func (u *ColorizeUi) parseJsonPath(input interface{}, template string) (*bytes.Buffer, error) {
	j := jsonpath.New("json-path")
	buf := new(bytes.Buffer)
	if err := j.Parse(template); err != nil {
		return buf, errors.New(fmt.Sprintf("Error parsing json: %v", err))
	}
	err := j.Execute(buf, input)
	if err != nil {
		return buf, errors.New(fmt.Sprintf("Error parsing value from input %v using template %s: %v ", input, template, err))
	}

	return buf, nil
}

func (u *ColorizeUi) Info(message string) {
	if !u.Quiet {
		u.Ui.Info(u.colorize(message, u.InfoColor))
	}
}

func (u *ColorizeUi) Error(message string) {
	u.Ui.Error(u.colorize(message, u.ErrorColor))
}

func (u *ColorizeUi) Warn(message string) {
	if !u.Quiet {
		u.Ui.Warn(u.colorize(message, u.WarnColor))
	}
}

func (u *ColorizeUi) colorize(message string, color string) string {
	if color == "" {
		return message
	}

	return u.Colorize.Color(fmt.Sprintf("%s%s", color, message))
}
