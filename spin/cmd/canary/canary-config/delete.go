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

	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

type deleteOptions struct {
	*canaryConfigOptions
}

const (
	deleteCanaryConfigShort = "Delete the provided canary config"
	deleteCanaryConfigLong  = "Delete the provided canary config"
)

func NewDeleteCmd(canaryConfigOptions *canaryConfigOptions) *cobra.Command {
	options := &deleteOptions{
		canaryConfigOptions: canaryConfigOptions,
	}
	cmd := &cobra.Command{
		Use:     "delete",
		Aliases: []string{"del"},
		Short:   deleteCanaryConfigShort,
		Long:    deleteCanaryConfigLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return deleteCanaryConfig(cmd, options, args)
		},
	}

	return cmd
}

func deleteCanaryConfig(cmd *cobra.Command, options *deleteOptions, args []string) error {
	id, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	resp, err := options.GateClient.V2CanaryConfigControllerApi.DeleteCanaryConfigUsingDELETE(
		options.GateClient.Context, id, &gate.V2CanaryConfigControllerApiDeleteCanaryConfigUsingDELETEOpts{})
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf(
			"Encountered an error deleting canary config, status code: %d\n", resp.StatusCode)
	}

	options.Ui.Success(fmt.Sprintf("Canary config %s deleted", id))
	return nil
}
