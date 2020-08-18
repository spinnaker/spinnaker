// Copyright (c) 2019, Kevin Reynolds.
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

	gate "github.com/spinnaker/spin/gateapi"

	"github.com/spinnaker/spin/util"

	"github.com/spf13/cobra"
)

type getProjectPipelinesOptions struct {
	*projectOptions
	expand bool
}

const (
	getPipelinesProjectShort   = "Get the pipelines for the specified project"
	getPipelinesProjectLong    = "Get the pipelines for the specified project"
	getPipelinesProjectExample = "usage: spin project get-pipelines [options] project-name"
)

func NewGetPipelinesCmd(prjOptions *projectOptions) *cobra.Command {
	options := &getProjectPipelinesOptions{
		projectOptions: prjOptions,
		expand:         false,
	}

	cmd := &cobra.Command{
		Use:     "get-pipelines",
		Short:   getPipelinesProjectShort,
		Long:    getPipelinesProjectLong,
		Example: getPipelinesProjectExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getProjectPipelines(cmd, options, args)
		},
	}

	return cmd
}

func getProjectPipelines(cmd *cobra.Command, options *getProjectPipelinesOptions, args []string) error {
	projectName, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	project, resp, err := options.GateClient.ProjectControllerApi.AllPipelinesForProjectUsingGET(options.GateClient.Context, projectName, &gate.ProjectControllerApiAllPipelinesForProjectUsingGETOpts{})
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
