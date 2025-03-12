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
	"fmt"
	"os"

	"github.com/spinnaker/spin/cmd"
	"github.com/spinnaker/spin/cmd/assembler"
)

func main() {
	command, options := cmd.NewCmdRoot(os.Stdout, os.Stderr)
	assembler.AddSubCommands(command, options)

	if err := command.Execute(); err != nil {
		if options.Ui != nil {
			options.Ui.Error(err.Error())
		} else {
			fmt.Fprintf(os.Stderr, "\n%v\n", err)
		}
		os.Exit(1)
	}
}
