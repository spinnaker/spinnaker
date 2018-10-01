// Copyright (c) 2018, Google, Inc.
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

package pipeline

import (
	"errors"
	"fmt"
	"net/http"

	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
)

type DeleteOptions struct {
	*pipelineOptions
	output      string
	application string
	name        string
}

var (
	deletePipelineShort   = "Delete the provided pipeline"
	deletePipelineLong    = "Delete the provided pipeline"
	deletePipelineExample = `
	usage: spin pipeline delete [options]

	Delete the provided pipeline

    --application: Spinnaker application the pipeline lives in
    --name: Name of the pipeline to delete

`
)

func NewDeleteCmd(appOptions pipelineOptions) *cobra.Command {
	options := DeleteOptions{
		pipelineOptions: &appOptions,
	}
	cmd := &cobra.Command{
		Use:     "delete",
		Aliases: []string{"del"},
		Short:   deletePipelineShort,
		Long:    deletePipelineLong,
		Example: deletePipelineExample,
		RunE: func(cmd *cobra.Command, args []string) error {
			return deletePipeline(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.application, "application", "a", "", "Spinnaker application the pipeline lives in")
	cmd.PersistentFlags().StringVarP(&options.name, "name", "n", "", "Name of the pipeline to delete")

	return cmd
}

func deletePipeline(cmd *cobra.Command, options DeleteOptions) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	if options.application == "" || options.name == "" {
		return errors.New("one of required parameters 'application' or 'name' not set")
	}
	resp, err := gateClient.PipelineControllerApi.DeletePipelineUsingDELETE(gateClient.Context, options.application, options.name)

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error deleting pipeline, status code: %d\n", resp.StatusCode)
	}

	util.UI.Info(util.Colorize().Color(fmt.Sprintf("[reset][bold][green]Pipeline deleted")))
	return nil
}
