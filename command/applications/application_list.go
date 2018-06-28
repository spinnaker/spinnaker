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
	"flag"
	"fmt"
	"net/http"
	"strings"

	"github.com/spinnaker/spin/command"
)

type ApplicationListCommand struct {
	ApiMeta command.ApiMeta
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *ApplicationListCommand) flagSet() *flag.FlagSet {
	cmd := "application list"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// listApplications calls the Gate endpoint to list the applications in Spinnaker.
func (c *ApplicationListCommand) listApplications() ([]interface{}, *http.Response, error) {
	// TODO(jacobkiefer): Turns out using the type 'HashMap' doesn't help much in the CLI
	// since json.Marshal* doesn't serialize it properly (it is not treated as a Map).
	// We need to think of a strategy (e.g. Concrete types or deferring to just returning Object)
	// In the cases where we use 'HashMap' currently.
	return c.ApiMeta.GateClient.ApplicationControllerApi.GetAllApplicationsUsingGET(c.ApiMeta.Context, map[string]interface{}{})
}

func (c *ApplicationListCommand) Run(args []string) int {
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

	appList, resp, err := c.listApplications()
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	if resp.StatusCode != http.StatusOK {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error saving application, status code: %d\n", resp.StatusCode))
		return 1
	}
	prettyString, err := json.MarshalIndent(appList, "", "  ")
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Failed to marshal application list payload: %d.\n", appList))
		return 1
	}
	c.ApiMeta.Ui.Output(fmt.Sprintf("%s\n", prettyString))
	return 0
}

func (c *ApplicationListCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin application list [options]

	List the all applications

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *ApplicationListCommand) Synopsis() string {
	return "List all applications."
}
