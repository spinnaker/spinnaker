// Copyright (c) 2020, Anosua Chini Mukhopadhyay
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

package project

import (
	"fmt"
	"net/http"

	"github.com/spf13/cobra"

	orca_tasks "github.com/spinnaker/spin/cmd/orca-tasks"
)

type deleteOptions struct {
	*projectOptions
	projectName string
}

var (
	deleteProjectShort = "Delete the provided project"
	deleteProjectLong  = "Delete the specified project"
)

func NewDeleteCmd(prjOptions *projectOptions) *cobra.Command {
	options := &saveOptions{
		projectOptions: prjOptions,
	}
	cmd := &cobra.Command{
		Use:   "delete",
		Short: deleteProjectShort,
		Long:  deleteProjectLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return deleteProject(cmd, options)
		},
	}
	cmd.PersistentFlags().StringVarP(&options.projectName, "name", "n", "", "name of the project")
	cobra.MarkFlagRequired(cmd.PersistentFlags(), "name")
	return cmd
}

func deleteProject(cmd *cobra.Command, options *saveOptions) error {
	projectName := options.projectName

	project, resp, err := options.GateClient.ProjectControllerApi.GetUsingGET1(options.GateClient.Context, projectName)
	if resp != nil {
		if resp.StatusCode == http.StatusNotFound {
			return fmt.Errorf("Project '%s' not found\n", projectName)
		} else if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("Encountered an error getting project, status code: %d\n", resp.StatusCode)
		}
	}

	deleteProjectTask := map[string]interface{}{
		"job":         []interface{}{map[string]interface{}{"type": "deleteProject", "project": project, "user": project["email"]}},
		"application": "spinnaker",
		"project":     projectName,
		"description": fmt.Sprintf("Delete Project: %s", projectName),
	}

	ref, _, err := options.GateClient.TaskControllerApi.TaskUsingPOST1(options.GateClient.Context, deleteProjectTask)
	if err != nil {
		return err
	}

	err = orca_tasks.WaitForSuccessfulTask(options.GateClient, ref, 5)
	if err != nil {
		return err
	}

	options.Ui.Success("Project delete succeeded")
	return nil
}
