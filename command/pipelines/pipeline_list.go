package pipelines

import (
	"encoding/json"
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
	return c.ApiMeta.GateClient.ApplicationControllerApi.GetPipelineConfigsForApplicationUsingGET(nil, application)
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
	prettyString, err := json.MarshalIndent(successPayload, "", "  ")
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Failed to marshal pipeline list payload: %d.\n", successPayload))
		return 1
	}
	c.ApiMeta.Ui.Output(fmt.Sprintf("%s\n", prettyString))
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
	return "List the provided pipeline."
}
