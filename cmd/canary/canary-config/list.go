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
	"net/http"

	"github.com/antihax/optional"
	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
)

type listOptions struct {
	*canaryConfigOptions
	application string
}

const (
	listCanaryConfigShort = "List the canary configs"
	listCanaryConfigLong  = "List the canary configs"
)

func NewListCmd(canaryConfigOptions *canaryConfigOptions) *cobra.Command {
	options := &listOptions{
		canaryConfigOptions: canaryConfigOptions,
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

func listCanaryConfig(cmd *cobra.Command, options *listOptions) error {
	successPayload, resp, err := options.GateClient.V2CanaryConfigControllerApi.GetCanaryConfigsUsingGET(
		options.GateClient.Context, &gate.V2CanaryConfigControllerApiGetCanaryConfigsUsingGETOpts{Application: optional.NewString(options.application)})
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf(
			"Encountered an error listing canary configs, status code: %d\n",
			resp.StatusCode)
	}

	options.Ui.JsonOutput(successPayload)
	return nil
}
