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
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/cmd/orca-tasks"
	"github.com/spinnaker/spin/util"
	"strings"
)

type SaveOptions struct {
	*applicationOptions
	applicationFile string
	applicationName string
	ownerEmail      string
	cloudProviders  *[]string
}

var (
	saveApplicationShort   = "Save the provided application"
	saveApplicationLong    = "Save the specified application"
)

func NewSaveCmd(appOptions applicationOptions) *cobra.Command {
	options := SaveOptions{
		applicationOptions: &appOptions,
	}
	cmd := &cobra.Command{
		Use:     "save",
		Short:   saveApplicationShort,
		Long:    saveApplicationLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return saveApplication(cmd, options)
		},
	}
	cmd.PersistentFlags().StringVarP(&options.applicationFile, "file", "", "", "path to the application file")
	cmd.PersistentFlags().StringVarP(&options.applicationName, "application-name", "", "", "name of the application")
	cmd.PersistentFlags().StringVarP(&options.ownerEmail, "owner-email", "", "", "email of the application owner")
	options.cloudProviders = cmd.PersistentFlags().StringArrayP("cloud-providers", "", []string{}, "cloud providers configured for this application")

	return cmd
}

func saveApplication(cmd *cobra.Command, options SaveOptions) error {
	// TODO(jacobkiefer): Should we check for an existing application of the same name?
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	initialApp, err := util.ParseJsonFromFileOrStdin(options.applicationFile, true)
	if err != nil {
		return fmt.Errorf("Could not parse supplied application: %v.\n", err)
	}

	var app map[string]interface{}
	if initialApp != nil && len(initialApp) > 0 {
		app = initialApp
		if len(*options.cloudProviders) != 0 {
			util.UI.Warn("Overriding application cloud providers with explicit flag values.\n")
			app["cloudProviders"] = strings.Join(*options.cloudProviders, ",")
		}
		if options.applicationName != "" {
			util.UI.Warn("Overriding application name with explicit flag values.\n")
			app["name"] = options.applicationName
		}
		if options.ownerEmail != "" {
			util.UI.Warn("Overriding application owner email with explicit flag values.\n")
			app["email"] = options.ownerEmail
		}
		// TODO(jacobkiefer): Add validation for valid cloudProviders and well-formed emails.
		if !(app["cloudProviders"] != nil && app["name"] != "" && app["email"] != "") {
			return errors.New("Required application parameter missing, exiting...")
		}
	} else {
		if options.applicationName == "" || options.ownerEmail == "" || len(*options.cloudProviders) == 0 {
			return errors.New("Required application parameter missing, exiting...")
		}
		app = map[string]interface{}{
			"cloudProviders": strings.Join(*options.cloudProviders, ","),
			"instancePort":   80,
			"name":           options.applicationName,
			"email":          options.ownerEmail,
		}
	}

	createAppTask := map[string]interface{}{
		"job":         []interface{}{map[string]interface{}{"type": "createApplication", "application": app}},
		"application": app["name"],
		"description": fmt.Sprintf("Create Application: %s", app["name"]),
	}

	ref, _, err := gateClient.TaskControllerApi.TaskUsingPOST1(gateClient.Context, createAppTask)
	if err != nil {
		return err
	}

	err = orca_tasks.WaitForSuccessfulTask(gateClient, ref, 5)
	if err != nil {
		return err
	}

	util.UI.Info(util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Application save succeeded")))
	return nil
}
