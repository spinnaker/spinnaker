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
	"flag"
	"fmt"
	"net/http"
	"strings"

	"github.com/spinnaker/spin/command"
)

type ApplicationDeleteCommand struct {
	ApiMeta command.ApiMeta

	applicationName string
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *ApplicationDeleteCommand) flagSet() *flag.FlagSet {
	cmd := "application delete"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.applicationName, "application-name", "", "Name of the Spinnaker application to delete")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// deleteApplication calls the Gate endpoint to delete the application.
func (c *ApplicationDeleteCommand) deleteApplication() (map[string]interface{}, *http.Response, error) {
	appSpec := map[string]interface{}{
		"type": "deleteApplication",
		"application": map[string]interface{}{
			"name": c.applicationName,
		},
		"user": "anonymous", // TODO(jacobkiefer): How to rectify this from the auth context?
	}

	createAppTask := map[string]interface{}{
		"job":         []interface{}{appSpec},
		"application": c.applicationName,
		"description": fmt.Sprintf("Delete Application: %s", c.applicationName),
	}
	return c.ApiMeta.GateClient.TaskControllerApi.TaskUsingPOST1(nil, createAppTask)
}

func (c *ApplicationDeleteCommand) Run(args []string) int {
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

	if c.applicationName == "" {
		c.ApiMeta.Ui.Error("Required parameter 'applicationName' not set.\n")
		return 1
	}
	_, resp, err := c.deleteApplication()

	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	if resp.StatusCode != http.StatusOK {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error deleting application, status code: %d\n", resp.StatusCode))
		return 1
	}

	c.ApiMeta.Ui.Output(c.ApiMeta.Colorize().Color(fmt.Sprintf("[reset][bold][green]Application deleted")))
	return 0
}

func (c *ApplicationDeleteCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin application delete [options]

	Delete the provided application

    --applicationName: Name of the Spinnaker application to delete

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *ApplicationDeleteCommand) Synopsis() string {
	return "Delete the specified application."
}
