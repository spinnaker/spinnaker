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
	"fmt"
	"net/http"

	gate "github.com/spinnaker/spin/gateapi"

	"github.com/spf13/cobra"

	"github.com/spinnaker/spin/util"
)

type getOptions struct {
	*accountOptions
}

var (
	getAccountShort   = "Get the specified account details"
	getAccountLong    = "Get the specified account details"
	getAccountExample = "usage: spin account get [options] account-name"
)

func NewGetCmd(accOptions *accountOptions) *cobra.Command {
	options := &getOptions{
		accountOptions: accOptions,
	}

	cmd := &cobra.Command{
		Use:     "get",
		Short:   getAccountShort,
		Long:    getAccountLong,
		Example: getAccountExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getAccount(cmd, options, args)
		},
	}

	return cmd
}

func getAccount(cmd *cobra.Command, options *getOptions, args []string) error {
	accountName, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	account, resp, err := options.GateClient.CredentialsControllerApi.GetAccountUsingGET(options.GateClient.Context, accountName, &gate.CredentialsControllerApiGetAccountUsingGETOpts{})
	if resp != nil {
		switch resp.StatusCode {
		case http.StatusOK:
			// pass
		case http.StatusNotFound:
			return fmt.Errorf("Account '%s' not found\n", accountName)
		default:
			return fmt.Errorf("Encountered an error getting account, status code: %d\n", resp.StatusCode)
		}
	}

	if err != nil {
		return err
	}
	options.Ui.JsonOutput(account)

	return nil
}
