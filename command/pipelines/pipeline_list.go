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

package pipelines

import (
	"flag"
	"fmt"
	"net/http"
	"strings"

	"github.com/spinnaker/spin/command"
)

type PipelineListCommand struct {
	ApiMeta command.ApiMeta

	application string
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *PipelineListCommand) flagSet() *flag.FlagSet {
	cmd := "pipeline list"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.application, "application", "", "Spinnaker application to list pipelines from")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// listPipelines calls the Gate endpoint to list the pipelines for the given application.
func (c *PipelineListCommand) listPipelines(application string) ([]interface{}, *http.Response, error) {
	return c.ApiMeta.GateClient.ApplicationControllerApi.GetPipelineConfigsForApplicationUsingGET(c.ApiMeta.Context, application)
}

func (c *PipelineListCommand) Run(args []string) int {
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

	if c.application == "" {
		c.ApiMeta.Ui.Error("Required parameter 'application' not set.\n")
		return 1
	}

	successPayload, resp, err := c.listPipelines(c.application)

	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	if resp.StatusCode != http.StatusOK {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error listing pipelines for application %s, status code: %d\n",
			c.application,
			resp.StatusCode))
		return 1
	}

	c.ApiMeta.Ui.JsonOutput(successPayload, c.ApiMeta.OutputFormat)
	return 0
}

func (c *PipelineListCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin pipeline list [options]

	List the pipelines for the provided application

    --application: Name of the application

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *PipelineListCommand) Synopsis() string {
	return "List the pipelines for the provided application."
}
