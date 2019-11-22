// Copyright 2019 New Relic Corporation. All rights reserved.
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

package account

import (
	"io"

	"github.com/spf13/cobra"
)

type accountOptions struct {
}

var (
	accountShort   = ""
	accountLong    = ""
	accountExample = ""
)

func NewAccountCmd(out io.Writer) *cobra.Command {
	options := accountOptions{}
	cmd := &cobra.Command{
		Use:     "account",
		Aliases: []string{"account", "acc"},
		Short:   accountShort,
		Long:    accountLong,
		Example: accountExample,
	}

	// create subcommands
	cmd.AddCommand(NewGetCmd(options))
	cmd.AddCommand(NewListCmd(options))
	return cmd
}
