package applications

import (
	"flag"
	"fmt"
	"net/http"
	"strings"

	"github.com/spinnaker/spin/command"
	"github.com/spinnaker/spin/util"
)

type ApplicationSaveCommand struct {
	ApiMeta command.ApiMeta

	applicationName string
	ownerEmail      string
	cloudProviders  util.FlagStringArray
}

// flagSet adds all options for this command to the flagset and returns the
// flagset object for further modification by subcommands.
func (c *ApplicationSaveCommand) flagSet() *flag.FlagSet {
	cmd := "application save"

	f := c.ApiMeta.GlobalFlagSet(cmd)
	f.StringVar(&c.applicationName, "application-name", "", "Name of the application")
	f.StringVar(&c.ownerEmail, "owner-email", "", "Email of the application owner")
	f.Var(&c.cloudProviders, "cloud-providers", "Cloud providers configured for this application")

	// TODO auto-generate flag help rather than putting it in "Help"
	f.Usage = func() {
		c.ApiMeta.Ui.Error(c.Help())
	}

	return f
}

// saveApplication calls the Gate endpoint to save the application.
func (c *ApplicationSaveCommand) saveApplication() (map[string]interface{}, *http.Response, error) {
	appSpec := map[string]interface{}{
		"type": "createApplication",
		"application": map[string]interface{}{
			"cloudProviders": c.cloudProviders,
			"instancePort":   80,
			"name":           c.applicationName,
			"email":          c.ownerEmail,
		},
		"user": "anonymous", // TODO(jacobkiefer): How to rectify this from the auth context?
	}

	createAppTask := map[string]interface{}{
		"job":         []interface{}{appSpec},
		"application": c.applicationName,
		"description": "Create Application: ",
	}
	return c.ApiMeta.GateClient.TaskControllerApi.TaskUsingPOST1(nil, createAppTask)
}

func (c *ApplicationSaveCommand) validateCommand() bool {
	if c.applicationName == "" || c.ownerEmail == "" || len(c.cloudProviders) == 0 {
		c.ApiMeta.Ui.Error("Required application parameter missing.\n")
		return false
	}
	// TODO(jacobkiefer): Add validation for valid cloudProviders and well-formed emails.
	return true
}

func (c *ApplicationSaveCommand) Run(args []string) int {
	// TODO(jacobkiefer): Should we check for an existing application of the same name?
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

	if !c.validateCommand() {
		return 1
	}

	_, resp, err := c.saveApplication()

	if err != nil {
		c.ApiMeta.Ui.Error(fmt.Sprintf("%s\n", err))
		return 1
	}

	if resp.StatusCode != http.StatusOK {
		c.ApiMeta.Ui.Error(fmt.Sprintf("Encountered an error saving application, status code: %d\n", resp.StatusCode))
		return 1
	}

	c.ApiMeta.Ui.Output(c.ApiMeta.Colorize().Color(fmt.Sprintf("[reset][bold][green]Application save succeeded")))
	return 0
}

func (c *ApplicationSaveCommand) Help() string {
	help := fmt.Sprintf(`
usage: spin application save [options]

	Save the provided application

    --application-name: Name of the application
    --owner-email: Email of the application owner
    --cloud-providers: List of configured cloud providers

%s`, c.ApiMeta.Help())
	return strings.TrimSpace(help)
}

func (c *ApplicationSaveCommand) Synopsis() string {
	return "Save the provided application."
}
