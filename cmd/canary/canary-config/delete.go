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

type DeleteOptions struct {
	*canaryConfigOptions
}

const (
	deleteCanaryConfigShort = "Delete the provided canary config"
	deleteCanaryConfigLong  = "Delete the provided canary config"
)

func NewDeleteCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:     "delete",
		Aliases: []string{"del"},
		Short:   deleteCanaryConfigShort,
		Long:    deleteCanaryConfigLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return deleteCanaryConfig(cmd, args)
		},
	}

	return cmd
}

func deleteCanaryConfig(cmd *cobra.Command, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	id, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	resp, err := gateClient.V2CanaryConfigControllerApi.DeleteCanaryConfigUsingDELETE(
		gateClient.Context, id, map[string]interface{}{})

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf(
			"Encountered an error deleting canary config, status code: %d\n", resp.StatusCode)
	}

	util.UI.Info(
		util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Canary config %s deleted", id)))
	return nil
}
