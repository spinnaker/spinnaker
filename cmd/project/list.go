package project

import (
    "fmt"
    "net/http"

    "github.com/spf13/cobra"
)

type listOptions struct {
    *projectOptions
    expand bool
}

var (
    listProjectShort   = "List the all projects"
    listProjectLong    = "List the all projects"
    listProjectExample = "usage: spin projects list [options]"
)

func NewListCmd(prjOptions *projectOptions) *cobra.Command {
    options := &listOptions{
        projectOptions: prjOptions,
    }
    cmd := &cobra.Command{
        Use:     "list",
        Aliases: []string{"ls"},
        Short:   listProjectShort,
        Long:    listProjectLong,
        Example: listProjectExample,
        RunE: func(cmd *cobra.Command, args []string) error {
            return listProject(cmd, options, args)
        },
    }
    return cmd
}

func listProject(cmd *cobra.Command, options *listOptions, args []string) error {
    projectList, resp, err := options.GateClient.ProjectControllerApi.AllUsingGET3(options.GateClient.Context)
    if err != nil {
        return err
    }

    if resp.StatusCode != http.StatusOK {
        return fmt.Errorf("Encountered error retrieving projects, status code: %d\n", resp.StatusCode)
    }

    options.Ui.JsonOutput(projectList)
    return nil
}
