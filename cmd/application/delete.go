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
	"github.com/spinnaker/spin/cmd/orca-tasks"
	"net/http"

	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
)

type DeleteOptions struct {
	*applicationOptions
}

var (
	deleteApplicationShort   = "Delete the specified application"
	deleteApplicationLong    = "Delete the provided application --application-name: Name of the Spinnaker application to delete"
	deleteApplicationExample = "usage: spin application delete [options] applicationName"
)

func NewDeleteCmd(appOptions applicationOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:     "delete",
		Aliases: []string{"del"},
		Short:   deleteApplicationShort,
		Long:    deleteApplicationLong,
		Example: deleteApplicationExample,
		RunE:    deleteApplication,
	}
	return cmd
}

func deleteApplication(cmd *cobra.Command, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	applicationName, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	appSpec := map[string]interface{}{
		"type": "deleteApplication",
		"application": map[string]interface{}{
			"name": applicationName,
		},
	}

	_, resp, err := gateClient.ApplicationControllerApi.GetApplicationUsingGET(gateClient.Context, applicationName, map[string]interface{}{"expand": false})

	if resp != nil && resp.StatusCode == http.StatusNotFound {
		return fmt.Errorf("Attempting to delete application '%s' which does not exist, exiting...", applicationName)
	}

	if err != nil {
		return fmt.Errorf("Encountered an error checking application existence, status code: %d\n", resp.StatusCode)
	}

	deleteAppTask := map[string]interface{}{
		"job":         []interface{}{appSpec},
		"application": applicationName,
		"description": fmt.Sprintf("Delete Application: %s", applicationName),
	}

	taskRef, resp, err := gateClient.TaskControllerApi.TaskUsingPOST1(gateClient.Context, deleteAppTask)
	if err != nil {
		return err
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error deleting application, status code: %d\n", resp.StatusCode)
	}

	err = orca_tasks.WaitForSuccessfulTask(gateClient, taskRef, 5)
	if err != nil {
		return err
	}

	util.UI.Output(util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Application deleted")))
	return nil
}
