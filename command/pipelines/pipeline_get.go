package pipelines

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"strings"

	"github.com/spinnaker/spin/command"
)

type PipelineGetCommand struct {
	ApiMeta command.ApiMeta

	application string
	name        string
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *PipelineGetCommand) flagSet() *flag.FlagSet {
	cmd := "pipeline get"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.application, "application", "", "Spinnaker application the pipeline belongs to")
	f.StringVar(&c.name, "name", "", "Name of the pipeline")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// getPipeline calls the Gate endpoint to get the pipelines for the given id.
func (c *PipelineGetCommand) getPipeline() (map[string]interface{}, *http.Response, error) {
	return c.ApiMeta.GateClient.ApplicationControllerApi.GetPipelineConfigUsingGET(nil, c.application, c.name)
}

func (c *PipelineGetCommand) Run(args []string) int {
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

	successPayload, resp, err := c.getPipeline()

	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	if resp.StatusCode != http.StatusOK {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error getting pipeline in application %s with name %s, status code: %d\n",
			c.application,
			c.name,
			resp.StatusCode))
		return 1
	}
	prettyString, err := json.MarshalIndent(successPayload, "", "  ")
	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Failed to marshal pipeline get payload: %v.\n", successPayload))
		return 1
	}
	c.ApiMeta.Ui.Output(fmt.Sprintf("%s\n", prettyString))
	return 0
}

func (c *PipelineGetCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin pipeline get [options]

	List the pipelines for the provided application

    --id: Id of the pipeline

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *PipelineGetCommand) Synopsis() string {
	return "Get the pipeline with the provided name from the provided application."
}
