package command

import (
	"flag"
	"fmt"
	"strings"
)

type PipelineSaveCommand struct {
	ApiMeta

	pipelineFile string
}

func (c *PipelineSaveCommand) flagSet() *flag.FlagSet {
	cmd := "pipeline save"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.pipelineFile, "f", "", "Path to the pipeline file")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		fmt.Printf(c.Help())
	}

	return f
}

func (c *PipelineSaveCommand) Run(args []string) int {
	f := c.flagSet()

	var err error
	if err = f.Parse(args); err != nil {
		fmt.Printf("%s\n", err)
		return 1
	}

	fmt.Printf("Talking to gate: %v\n", c.ApiMeta.gateEndpoint);

	// TODO actually call gate

	fmt.Printf("success\n")

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
