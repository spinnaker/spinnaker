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
	"time"

	"github.com/spinnaker/spin/command"
	gate "github.com/spinnaker/spin/gateapi"
)

type PipelineExecuteCommand struct {
	ApiMeta command.ApiMeta

	application string
	name        string
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *PipelineExecuteCommand) flagSet() *flag.FlagSet {
	cmd := "pipeline execute"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.application, "application", "", "Spinnaker application the pipeline lives in")
	f.StringVar(&c.name, "name", "", "Name of the pipeline to execute")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// executePipeline calls the Gate endpoint to execute the pipeline.
func (c *PipelineExecuteCommand) executePipeline() (gate.HttpEntity, *http.Response, error) {
	entity, resp, err := c.ApiMeta.GateClient.PipelineControllerApi.InvokePipelineConfigUsingPOST1(c.ApiMeta.Context,
		c.application,
		c.name,
		map[string]interface{}{"type": "manual"})
	if err != nil {
		return gate.HttpEntity{}, nil, err
	}

	return entity, resp, err
}

func (c *PipelineExecuteCommand) queryExecution() ([]interface{}, *http.Response, error) {
	return c.ApiMeta.GateClient.ExecutionsControllerApi.SearchForPipelineExecutionsByTriggerUsingGET(
		c.ApiMeta.Context,
		c.application,
		map[string]interface{}{
			"pipelineName": c.name,
			"statuses":     "RUNNING",
		})
}

func (c *PipelineExecuteCommand) Run(args []string) int {
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

	if c.application == "" || c.name == "" {
		c.ApiMeta.Ui.Error("One of required parameters 'application' or 'name' not set.\n")
		return 1
	}
	_, resp, err := c.executePipeline()

	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Execute pipeline failed with response: %v and error: %s\n", resp, err))
		return 1
	}

	if resp.StatusCode != http.StatusAccepted {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error executing pipeline, status code: %d\n", resp.StatusCode))
		return 1
	}

	executions := make([]interface{}, 0)
	attempts := 0
	for len(executions) == 0 && attempts < 5 {
		executions, resp, err = c.queryExecution()
		attempts += 1
		time.Sleep(time.Duration(attempts*attempts) * time.Second)
	}
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error querying pipeline execution, status code: %d\n", resp.StatusCode))
		return 1
	}
	if len(executions) == 0 {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Unable to start any executions, server response was: %v", resp))
		return 1
	}

	refIds := make([]string, 0)
	for _, execution := range executions {
		refIds = append(refIds, execution.(map[string]interface{})["id"].(string))
	}
	c.ApiMeta.Ui.Output(fmt.Sprintf("%v", refIds))
	return 0
}

func (c *PipelineExecuteCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin pipeline execute [options]

	Execute the provided pipeline

    --application: Spinnaker application the pipeline lives in
    --name: Name of the pipeline to execute

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *PipelineExecuteCommand) Synopsis() string {
	return "Execute the provided pipeline."
}
