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

	"github.com/spinnaker/spin/util"

	"github.com/spf13/cobra"
)

type getProjectOptions struct {
	*projectOptions
}

const (
	getProjectShort   = "Get the config for the specified project"
	getProjectLong    = "Get the config for the specified project"
	getProjectExample = "usage: spin project [options] project-name"
)

func NewGetCmd(prjOptions *projectOptions) *cobra.Command {
	options := &getProjectOptions{
		projectOptions: prjOptions,
	}

	cmd := &cobra.Command{
		Use:     "get",
		Short:   getProjectShort,
		Long:    getProjectLong,
		Example: getProjectExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getProject(cmd, options, args)
		},
	}

	return cmd
}

func getProject(cmd *cobra.Command, options *getProjectOptions, args []string) error {
	projectName, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	project, resp, err := options.GateClient.ProjectControllerApi.GetUsingGET1(options.GateClient.Context, projectName)
	if resp != nil {
		if resp.StatusCode == http.StatusNotFound {
			return fmt.Errorf("Project '%s' not found\n", projectName)
		} else if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("Encountered an error getting project, status code: %d\n", resp.StatusCode)
		}
	}

	if err != nil {
		return err
	}
	options.Ui.JsonOutput(project)

	return nil
}
