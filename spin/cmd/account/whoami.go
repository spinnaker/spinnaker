// Copyright 2021, Han Byul Ko.
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
	"fmt"
	"net/http"

	"github.com/spf13/cobra"
)

type whoamiOptions struct {
	*accountOptions
	expand bool
}

var (
	whoamiAccountShort   = "Show information about user who is making the API calls via spin"
	whoamiAccountLong    = "Show information about user who is making the API calls via spin"
	whoamiAccountExample = "usage: spin account whoami [options]"
)

func NewWhoamiCmd(accOptions *accountOptions) *cobra.Command {
	options := &whoamiOptions{
		accountOptions: accOptions,
		expand:         false,
	}

	cmd := &cobra.Command{
		Use:     "whoami",
		Aliases: []string{"user"},
		Short:   whoamiAccountShort,
		Long:    whoamiAccountLong,
		Example: whoamiAccountExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return whoamiAccount(cmd, options, args)
		},
	}
	return cmd
}

func whoamiAccount(cmd *cobra.Command, options *whoamiOptions, args []string) error {
	whoami, resp, err := options.GateClient.AuthControllerApi.UserUsingGET(options.GateClient.Context)
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error whoamiing accounts, status code: %d\n", resp.StatusCode)
	}

	options.Ui.JsonOutput(whoami)
	return nil
}
