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
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
)

type GetOptions struct {
	*accountOptions
}

var (
	getAccountShort   = "Get the specified account details"
	getAccountLong    = "Get the specified account details"
	getAccountExample = "usage: spin account get [options] account-name"
)

func NewGetCmd(accOptions accountOptions) *cobra.Command {
	options := GetOptions{
		accountOptions: &accOptions,
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

func getAccount(cmd *cobra.Command, options GetOptions, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	accountName, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	account, resp, err := gateClient.CredentialsControllerApi.GetAccountUsingGET(gateClient.Context, accountName, map[string]interface{}{})
	if resp != nil {
		if resp.StatusCode == http.StatusNotFound {
			return fmt.Errorf("Account '%s' not found\n", accountName)
		} else if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("Encountered an error getting account, status code: %d\n", resp.StatusCode)
		}
	}

	if err != nil {
		return err
	}
	util.UI.JsonOutput(account, util.UI.OutputFormat)

	return nil
}
