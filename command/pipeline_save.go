package command

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io/ioutil"
	"net/http"
	"strings"
)

const (
	APPLICATION_JSON = "application/json"
)

type PipelineSaveCommand struct {
	ApiMeta

	pipelineFile string
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *PipelineSaveCommand) flagSet() *flag.FlagSet {
	cmd := "pipeline save"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.pipelineFile, "file", "", "Path to the pipeline file")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.Ui.Error(c.Help())
	}

	return f
}

// parsePipelineFile reads and deserializes the input pipeline file.
func (c *PipelineSaveCommand) parsePipelineFile() (map[string]interface{}, error) {
	dat, err := ioutil.ReadFile(c.pipelineFile)
	if err != nil {
		return nil, err
	}

	var pipelineJson map[string]interface{}
	err = json.Unmarshal(dat, &pipelineJson)
	if err != nil {
		return nil, err
	}
	return pipelineJson, nil
}

// pipelineIsValid validates that the passed pipelineJson is formatted properly.
// Flag overrides should be processed before this is called.
func (c *PipelineSaveCommand) pipelineIsValid(pipelineJson map[string]interface{}) bool {
	// TODO: Dry-run pipeline save and report errors?
	var exists bool
	if _, exists = pipelineJson["name"]; !exists {
		c.Ui.Error("Required pipeline key 'name' missing...\n")
		return false
	}

	if _, exists = pipelineJson["application"]; !exists {
		c.Ui.Error("Required pipeline key 'application' missing...\n")
		return false
	}

	if _, exists = pipelineJson["id"]; !exists {
		c.Ui.Error("Required pipeline key 'id' missing...\n")
		return false
	}
	return true
}

// savePipeline calls the Gate endpoint to save the pipeline.
func (c *PipelineSaveCommand) savePipeline(pipelineJson map[string]interface{}) (*http.Response, error) {
	payload, err := json.Marshal(pipelineJson)
	if err != nil {
		return nil, err
	}

	pipelineEndpoint := c.ApiMeta.gateEndpoint + "/pipelines"
	resp, err := http.Post(pipelineEndpoint,
		APPLICATION_JSON,
		bytes.NewReader(payload))

	if err != nil {
		return nil, err
	}

	return resp, err
}

func (c *PipelineSaveCommand) Run(args []string) int {
	var err error
	args, err = c.ApiMeta.process(args)

	f := c.flagSet()

	if err = f.Parse(args); err != nil {
		c.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	pipelineJson, err := c.parsePipelineFile()
	if err != nil {
		c.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	c.Ui.Output(fmt.Sprintf("Parsed submitted pipeline: %s\n", pipelineJson))
	if valid := c.pipelineIsValid(pipelineJson); !valid {
		return 1
	}

	resp, err := c.savePipeline(pipelineJson)

	if err != nil {
		c.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	if resp.StatusCode != http.StatusOK {
		c.Ui.Error(fmt.Sprintf("Encountered an error saving pipeline, status code: %d\n", resp.StatusCode))
		return 1
	}

	c.Ui.Output(c.Colorize().Color(fmt.Sprintf("[reset][bold][green]Pipeline save succeeded")))
	return 0
}

func (c *PipelineSaveCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin pipeline save [options]

	Save the provided pipeline

    -file: Path to the pipeline file

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *PipelineSaveCommand) Synopsis() string {
	return "Save the provided pipeline."
}
