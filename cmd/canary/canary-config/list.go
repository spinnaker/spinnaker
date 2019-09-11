// Copyright (c) 2019, Waze, Inc.
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

package canary_config

import (
	"fmt"
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
)

type ListOptions struct {
	*canaryConfigOptions
	application string
}

const (
	listCanaryConfigShort = "List the canary configs"
	listCanaryConfigLong  = "List the canary configs"
)

func NewListCmd(canaryConfigOptions canaryConfigOptions) *cobra.Command {
	options := ListOptions{
		canaryConfigOptions: &canaryConfigOptions,
	}
	cmd := &cobra.Command{
		Use:     "list",
		Aliases: []string{"ls"},
		Short:   listCanaryConfigShort,
		Long:    listCanaryConfigLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return listCanaryConfig(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(
		&options.application, "application", "a", "", "application to list")

	return cmd
}

func listCanaryConfig(cmd *cobra.Command, options ListOptions) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	successPayload, resp, err := gateClient.V2CanaryConfigControllerApi.GetCanaryConfigsUsingGET(
		gateClient.Context, map[string]interface{}{"application": options.application})

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf(
			"Encountered an error listing canary configs, status code: %d\n",
			resp.StatusCode)
	}

	util.UI.JsonOutput(successPayload, util.UI.OutputFormat)
	return nil
}
