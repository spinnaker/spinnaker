// Copyright (c) 2018, Google, Inc.
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
	"fmt"
	"net/http"

	"github.com/antihax/optional"
	"github.com/spf13/cobra"

	gate "github.com/spinnaker/spin/gateapi"
	"github.com/spinnaker/spin/util"
)

type getOptions struct {
	*applicationOptions
	expand bool
}

var (
	getApplicationShort   = "Get the specified application"
	getApplicationLong    = "Get the specified application"
	getApplicationExample = "usage: spin application get [options] application-name"
)

func NewGetCmd(appOptions *applicationOptions) *cobra.Command {
	options := &getOptions{
		applicationOptions: appOptions,
		expand:             false,
	}

	cmd := &cobra.Command{
		Use:     "get",
		Aliases: []string{"get"},
		Short:   getApplicationShort,
		Long:    getApplicationLong,
		Example: getApplicationExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getApplication(cmd, options, args)
		},
	}

	// Note that false here means defaults to false, and flips to true if the flag is present.
	cmd.PersistentFlags().BoolVarP(&options.expand, "expand", "x", false, "expand app payload to include clusters")

	return cmd
}

func getApplication(cmd *cobra.Command, options *getOptions, args []string) error {
	applicationName, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	app, resp, err := options.GateClient.ApplicationControllerApi.GetApplicationUsingGET(options.GateClient.Context, applicationName, &gate.ApplicationControllerApiGetApplicationUsingGETOpts{Expand: optional.NewBool(options.expand)})
	if resp != nil {
		switch resp.StatusCode {
		case http.StatusOK:
			// pass
		case http.StatusNotFound:
			return fmt.Errorf("Application '%s' not found\n", applicationName)
		default:
			return fmt.Errorf("Encountered an error getting application, status code: %d\n", resp.StatusCode)
		}
	}

	if err != nil {
		return err
	}

	if options.expand {
		// NOTE: expand returns the actual attributes as well as the app's cluster details, nested in
		// their own fields. This means that the expanded output can't be submitted as input to `save`.
		options.Ui.JsonOutput(app)
	} else {
		// NOTE: app GET wraps the actual app attributes in an 'attributes' field.
		options.Ui.JsonOutput(app["attributes"])
	}

	return nil
}
