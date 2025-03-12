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
	"errors"
	"fmt"
	"net/http"

	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

type getOptions struct {
	*canaryConfigOptions
	id string
}

const (
	getCanaryConfigShort = "Get the canary config with the provided id"
	getCanaryConfigLong  = "Get the specified canary config"
)

func NewGetCmd(canaryConfigOptions *canaryConfigOptions) *cobra.Command {
	options := &getOptions{
		canaryConfigOptions: canaryConfigOptions,
	}
	cmd := &cobra.Command{
		Use:   "get",
		Short: getCanaryConfigShort,
		Long:  getCanaryConfigLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getCanaryConfig(cmd, options, args)
		},
	}

	cmd.PersistentFlags().StringVar(&options.id, "id", "", "id of the canary config")

	return cmd
}

func getCanaryConfig(cmd *cobra.Command, options *getOptions, args []string) error {
	var err error
	id := options.id
	if id == "" {
		id, err = util.ReadArgsOrStdin(args)
		if err != nil {
			return err
		}
		if id == "" {
			return errors.New("no canary config id supplied, exiting")
		}
	}

	successPayload, resp, err := options.GateClient.V2CanaryConfigControllerApi.GetCanaryConfigUsingGET(
		options.GateClient.Context, id, &gate.V2CanaryConfigControllerApiGetCanaryConfigUsingGETOpts{})
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error getting canary config with id %s, status code: %d\n",
			id,
			resp.StatusCode)
	}

	options.Ui.JsonOutput(successPayload)
	return nil
}
