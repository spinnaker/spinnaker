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

	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
)

type ListOptions struct {
	*applicationOptions
}

var (
	listApplicationShort   = "List the all applications"
	listApplicationLong    = "List the all applications"
	listApplicationExample = "usage: spin application list [options]"
)

func NewListCmd(appOptions applicationOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:     "list",
		Aliases: []string{"ls"},
		Short:   listApplicationShort,
		Long:    listApplicationLong,
		Example: listApplicationExample,
		RunE:    listApplication,
	}
	return cmd
}

func listApplication(cmd *cobra.Command, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}
	appList, resp, err := gateClient.ApplicationControllerApi.GetAllApplicationsUsingGET(gateClient.Context, map[string]interface{}{})
	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error saving application, status code: %d\n", resp.StatusCode)
	}

	util.UI.JsonOutput(appList, util.UI.OutputFormat)
	return nil
}
