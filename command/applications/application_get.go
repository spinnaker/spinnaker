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

type ApplicationGetCommand struct {
	ApiMeta command.ApiMeta

	applicationName string
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *ApplicationGetCommand) flagSet() *flag.FlagSet {
	cmd := "application get"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// getApplication calls the Gate endpoint to get the specified application in Spinnaker.
func (c *ApplicationGetCommand) getApplication() (map[string]interface{}, *http.Response, error) {
	// TODO(jacobkiefer): Turns out using the type 'HashMap' doesn't help much in the CLI
	// since json.Marshal* doesn't serialize it properly (it is not treated as a Map).
	// We need to think of a strategy (e.g. Concrete types or deferring to just returning Object)
	// In the cases where we use 'HashMap' currently.
	return c.ApiMeta.GateClient.ApplicationControllerApi.GetApplicationUsingGET(c.ApiMeta.Context, c.applicationName, map[string]interface{}{})
}

func (c *ApplicationGetCommand) Run(args []string) int {
	var err error
	f := c.flagSet()
	if err = f.Parse(args); err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	_, err = c.ApiMeta.Process(args)
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	args = f.Args()
	if len(args) != 1 {
		f.Usage()
		return 1
	}

	c.applicationName = args[0]
	if c.applicationName == "" {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Application name required...\n"))
		return 1
	}

	app, resp, err := c.getApplication()
	if resp != nil {
		if resp.StatusCode == http.StatusNotFound {
			c.ApiMeta.Ui.Error(fmt.Sprintf("Application '%s' not found\n", c.applicationName))
			return 1
		} else if resp.StatusCode != http.StatusOK {
			c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error getting application, status code: %d\n", resp.StatusCode))
			return 1
		}
	}

	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	c.ApiMeta.Ui.JsonOutput(app, c.ApiMeta.OutputFormat)
	return 0
}

func (c *ApplicationGetCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin application get [options] applicationName

	Get the specified application

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *ApplicationGetCommand) Synopsis() string {
	return "Get the specified application."
}
