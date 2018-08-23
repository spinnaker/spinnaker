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

package applications

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/spinnaker/spin/command"
	"github.com/spinnaker/spin/util"
)

type ApplicationSaveCommand struct {
	ApiMeta command.ApiMeta

	applicationFile string
	applicationName string
	ownerEmail      string
	cloudProviders  util.FlagStringArray
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *ApplicationSaveCommand) flagSet() *flag.FlagSet {
	cmd := "application save"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.applicationFile, "file", "", "Path to the application file")
	f.StringVar(&c.applicationName, "application-name", "", "Name of the application")
	f.StringVar(&c.ownerEmail, "owner-email", "", "Email of the application owner")
	f.Var(&c.cloudProviders, "cloud-providers", "Cloud providers configured for this application")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// parseApplicationFile reads and deserializes the application input from Stdin or a file.
func (c *ApplicationSaveCommand) parseApplicationFile() (map[string]interface{}, error) {
	var fromFile *os.File
	var err error
	var applicationJson map[string]interface{}

	if c.applicationFile != "" {
		fromFile, err = os.Open(c.applicationFile)
		if err != nil {
			return nil, err
		}
	} else {
		fromFile = os.Stdin
	}

	fi, err := fromFile.Stat()
	if err != nil {
		return nil, err
	}

	pipedStdin := (fi.Mode() & os.ModeCharDevice) == 0
	if fi.Size() <= 0 && !pipedStdin {
		// Create app based on flag input.
		c.ApiMeta.Ui.Info("No json input, constructing application from flags.")
		return nil, nil
	}

	err = json.NewDecoder(fromFile).Decode(&applicationJson)
	if err != nil {
		return nil, err
	}
	return applicationJson, nil
}

// saveApplication calls the Gate endpoint to save the application.
func (c *ApplicationSaveCommand) saveApplication(initialApp map[string]interface{}) (map[string]interface{}, *http.Response, error) {
	var app map[string]interface{}
	if initialApp != nil && len(initialApp) > 0 {
		app = initialApp
		if len(c.cloudProviders) != 0 {
			c.ApiMeta.Ui.Warn("Overriding application cloud providers with explicit flag values.\n")
			app["cloudProviders"] = c.cloudProviders
		}
		if c.applicationName != "" {
			c.ApiMeta.Ui.Warn("Overriding application name with explicit flag values.\n")
			app["name"] = c.applicationName
		}
		if c.ownerEmail != "" {
			c.ApiMeta.Ui.Warn("Overriding application owner email with explicit flag values.\n")
			app["email"] = c.ownerEmail
		}

		if !c.validApp(app) {
			return nil, nil, errors.New("Required application parameter missing, exiting...")
		}
	} else {
		if c.applicationName == "" || c.ownerEmail == "" || len(c.cloudProviders) == 0 {
			return nil, nil, errors.New("Required application parameter missing, exiting...")
		}
		app = map[string]interface{}{
			"cloudProviders": c.cloudProviders,
			"instancePort":   80,
			"name":           c.applicationName,
			"email":          c.ownerEmail,
		}
	}

	createAppTask := map[string]interface{}{
		"job":         []interface{}{map[string]interface{}{"type": "createApplication", "application": app}},
		"application": app["name"],
		"description": fmt.Sprintf("Create Application: %s", app["name"]),
	}
	return c.ApiMeta.GateClient.TaskControllerApi.TaskUsingPOST1(c.ApiMeta.Context, createAppTask)
}

func (c *ApplicationSaveCommand) validApp(app map[string]interface{}) bool {
	// TODO(jacobkiefer): Add validation for valid cloudProviders and well-formed emails.
	providers, _ := app["cloudProviders"]
	name, _ := app["name"]
	email, _ := app["email"]
	return providers != nil && name != "" && email != ""
}

// queryTask queries the task for the given ref and returns whether it was
// successful or not.
func (c *ApplicationSaveCommand) queryTask(ref map[string]interface{}) (map[string]interface{}, *http.Response, error) {
	toks := strings.Split(ref["ref"].(string), "/")
	id := toks[len(toks)-1]

	return c.ApiMeta.GateClient.TaskControllerApi.GetTaskUsingGET1(c.ApiMeta.Context, id)
}

// TODO(jacobkiefer): Consider generalizing if we need these functions elsewhere.

func (c *ApplicationSaveCommand) taskCompleted(task map[string]interface{}) bool {
	taskStatus, exists := task["status"]
	if !exists {
		return false
	}

	COMPLETED := [...]string{"SUCCEEDED", "STOPPED", "SKIPPED", "TERMINAL", "FAILED_CONTINUE"}
	for _, status := range COMPLETED {
		if taskStatus == status {
			return true
		}
	}
	return false
}

func (c *ApplicationSaveCommand) taskSucceeded(task map[string]interface{}) bool {
	taskStatus, exists := task["status"]
	if !exists {
		return false
	}

	SUCCESSFUL := [...]string{"SUCCEEDED", "STOPPED", "SKIPPED"}
	for _, status := range SUCCESSFUL {
		if taskStatus == status {
			return true
		}
	}
	return false
}

func (c *ApplicationSaveCommand) Run(args []string) int {
	// TODO(jacobkiefer): Should we check for an existing application of the same name?
	var err error
	f := c.flagSet()
	if err = f.Parse(args); err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	args, err = c.ApiMeta.Process(args)
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	applicationJson, err := c.parseApplicationFile()
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	ref, _, err := c.saveApplication(applicationJson)
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	task, resp, err := c.queryTask(ref)
	attempts := 0
	for (task == nil || !c.taskCompleted(task)) && attempts < 5 {
		task, resp, err = c.queryTask(ref)
		attempts += 1
		time.Sleep(time.Duration(attempts*attempts) * time.Second)
	}

	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error saving application, status code: %d\n", resp.StatusCode))
		return 1
	}
	if !c.taskSucceeded(task) {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error saving application, task output was: %v\n", task))
		return 1
	}

	c.ApiMeta.Ui.Info(c.ApiMeta.Colorize().Color(fmt.Sprintf("[reset][bold][green]Application save succeeded")))
	return 0
}

func (c *ApplicationSaveCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin application save [options]

	Save the provided application

    --application-name: Name of the application
    --file: Path to the application file
    --owner-email: Email of the application owner
    --cloud-providers: List of configured cloud providers

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *ApplicationSaveCommand) Synopsis() string {
	return "Save the provided application."
}
