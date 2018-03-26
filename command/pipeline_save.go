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

	application string // Optional pipeline application, can be defined in the pipelineFile instead.

	pipelineName string // Optional pipeline name, can be defined in the pipelineFile instead.
}

func (c *PipelineSaveCommand) flagSet() *flag.FlagSet {
	cmd := "pipeline save"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.pipelineFile, "f", "", "Path to the pipeline file")
	f.StringVar(&c.application, "a", "", "Pipeline application")
	f.StringVar(&c.pipelineName, "n", "", "Pipeline name")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.Ui.Error(c.Help())
	}

	return f
}

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

func (c *PipelineSaveCommand) pipelineIsValid(pipelineJson map[string]interface{}) bool {
	var exists bool
	if _, exists = pipelineJson["name"]; !exists {
		c.Ui.Error("Required pipeline key 'name' missing...\n")
		return false
	}

	if _, exists = pipelineJson["application"]; !exists {
		c.Ui.Error("Required pipeline key 'application' missing...\n")
		return false
	}
	return true
}

func (c *PipelineSaveCommand) queryPipeline(pipelineJson map[string]interface{}) (string, error) {
	// TODO: Format URLs with https://golang.org/pkg/net/url/.
	pipelineConfigEndpoint := fmt.Sprintf("%s/applications/%s/pipelineConfigs", c.ApiMeta.gateEndpoint, pipelineJson["application"])
	resp, err := http.Get(pipelineConfigEndpoint)
	if err != nil {
		return "", err
	}

	defer resp.Body.Close()
	var configs []map[string]interface{}
	decoder := json.NewDecoder(resp.Body)
	err = decoder.Decode(&configs)
	if err != nil {
		return "", err
	}

	// TODO: Submit an Id on each pipeline save so that we don't have to query the pipeline Id.
	// This requires backend changes to Orca and Front50 to honor the Id on a new pipeline.
	var found map[string]interface{}
	for _, config := range configs {
		if config["name"] == pipelineJson["name"] {
			found = config
			break
		}
	}

	if found != nil {
		return found["id"].(string), nil
	} else {
		return "", nil
	}
}

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

	if c.application != "" {
		c.Ui.Output("Overriding pipeline application with user-supplied application")
		pipelineJson["application"] = c.application
	}
	if c.pipelineName != "" {
		c.Ui.Output("Overriding pipeline name with user-supplied name")
		pipelineJson["name"] = c.pipelineName
	}

	c.Ui.Output(fmt.Sprintf("Parsed submitted pipeline: %s\n", pipelineJson))
	if valid := c.pipelineIsValid(pipelineJson); !valid {
		return 1
	}

	id, err := c.queryPipeline(pipelineJson)
	if err != nil {
		c.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	var resp *http.Response
	if id == "" {
		resp, err = c.savePipeline(pipelineJson)
	} else {
		// Including the id in a pipeline save causes the backend to update in place.
		pipelineJson["id"] = id
		resp, err = c.savePipeline(pipelineJson)
	}

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

    -f: Path to the pipeline file

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *PipelineSaveCommand) Synopsis() string {
	return "Save the provided pipeline."
}
