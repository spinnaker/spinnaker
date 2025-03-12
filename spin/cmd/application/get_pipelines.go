// Copyright (c) 2020, Anosua "Chini" Mukhopadhyay
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

package application

import (
	"errors"
	"fmt"
	"net/http"

	"github.com/antihax/optional"
	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
)

type getPipelinesOptions struct {
	*pipelineOptions
	applicationName string
	expand          bool
	status          string
}

var (
	getPipelinesShort   = "Get the pipelines for the specified application"
	getPipelinesLong    = "Get the pipelines for the specified application"
	getPipelinesExample = "usage: spin application pipelines get [options] application-name status"
)

func NewGetPipelinesCmd(pipeOptions *pipelineOptions) *cobra.Command {
	options := &getPipelinesOptions{
		pipelineOptions: pipeOptions,
		expand:          false,
	}

	cmd := &cobra.Command{
		Use:     "get",
		Aliases: []string{"get"},
		Short:   getPipelinesShort,
		Long:    getPipelinesLong,
		Example: getPipelinesExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getPipelinesWithOutput(cmd, options, args)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.applicationName, "application-name", "a", "", "name of the application")
	cmd.PersistentFlags().StringVarP(&options.status, "status", "s", "", "status pipeline to search for")
	cmd.PersistentFlags().BoolVarP(&options.expand, "expand", "x", false, "expand app payload to include clusters")
	return cmd
}

func getPipelinesWithOutput(cmd *cobra.Command, options *getPipelinesOptions, args []string) error {
	app, err := getPipelines(cmd, options, args)
	if err != nil {
		return err
	}

	options.Ui.JsonOutput(app)
	return nil
}

func getPipelines(cmd *cobra.Command, options *getPipelinesOptions, args []string) ([]interface{}, error) {
	if options.applicationName == "" {
		return nil, errors.New("Application name must be passed in")
	}

	app, resp, err := options.GateClient.ApplicationControllerApi.GetPipelinesUsingGET(options.GateClient.Context, options.applicationName, &gate.ApplicationControllerApiGetPipelinesUsingGETOpts{Expand: optional.NewBool(options.expand), Statuses: optional.NewString(options.status)})
	if resp != nil {
		switch resp.StatusCode {
		case http.StatusOK:
			// pass
		case http.StatusNotFound:
			return nil, fmt.Errorf("Application '%s' not found\n", options.applicationName)
		default:
			return nil, fmt.Errorf("Encountered an error getting application, status code: %d\n", resp.StatusCode)
		}
	}

	if err != nil {
		return nil, err
	}

	return app, nil
}
