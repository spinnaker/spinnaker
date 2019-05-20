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
	"github.com/spinnaker/spin/util"
	"net/http"

	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
)

type GetOptions struct {
	*projectOptions
	expand bool
}

var (
	getProjectShort   = "Get the pipelines for the specified project"
	getProjectLong    = "Get the pipelines for the specified project"
	getProjectExample = "usage: spin project get-pipelines [options] project-name"
)

func NewGetCmd(prjOptions projectOptions) *cobra.Command {
	options := GetOptions{
		projectOptions: &prjOptions,
		expand: false,
	}

	cmd := &cobra.Command{
		Use:     "get-pipelines",
		Short:   getProjectShort,
		Long:    getProjectLong,
		Example: getProjectExample,
		RunE:    func(cmd *cobra.Command, args []string) error {
			return getProject(cmd, options, args)
		},
	}

	return cmd
}

func getProject(cmd *cobra.Command, options GetOptions, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	projectName, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return err
	}

	project, resp, err := gateClient.ProjectControllerApi.AllPipelinesForProjectUsingGET(gateClient.Context, projectName, map[string]interface{}{"expand": options.expand})
	if resp != nil {
		if resp.StatusCode == http.StatusNotFound {
			return fmt.Errorf("Project '%s' not found\n",projectName)
		} else if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("Encountered an error getting project, status code: %d\n", resp.StatusCode)
		}
	}

	if err != nil {
		return err
	}
	util.UI.JsonOutput(project, util.UI.OutputFormat)

	return nil
}
