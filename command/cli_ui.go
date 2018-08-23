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

package command

import (
	"fmt"

	"github.com/mitchellh/cli"
	"github.com/mitchellh/colorstring"
)

type ColorizeUi struct {
	Colorize    *colorstring.Colorize
	OutputColor string
	InfoColor   string
	ErrorColor  string
	WarnColor   string
	Ui          cli.Ui
	Quiet       bool
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
