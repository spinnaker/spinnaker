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

package pipeline_template

import (
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
)

type GetOptions struct {
	*pipelineTemplateOptions
	id string
	tag string
}

var (
	getPipelineTemplateShort = "Get the pipeline template with the provided id"
	getPipelineTemplateLong  = "Get the specified pipeline template"
)

func NewGetCmd(pipelineTemplateOptions pipelineTemplateOptions) *cobra.Command {
	options := GetOptions{
		pipelineTemplateOptions: &pipelineTemplateOptions,
	}
	cmd := &cobra.Command{
		Use:   "get",
		Short: getPipelineTemplateShort,
		Long:  getPipelineTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return getPipelineTemplate(cmd, options, args)
		},
	}

	cmd.PersistentFlags().StringVar(&options.id, "id", "", "id of the pipeline template")
	cmd.PersistentFlags().StringVar(&options.tag, "tag", "",
		"(optional) specific tag to query")

	return cmd
}

func getPipelineTemplate(cmd *cobra.Command, options GetOptions, args []string) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	id := options.id
	if id == "" {
		id, err = util.ReadArgsOrStdin(args)
		if err != nil {
			return err
		}
		if id == "" {
			return errors.New("no pipeline template id supplied, exiting")
		}
	}

	queryParams := map[string]interface{}{}
	if options.tag != "" {
		queryParams["tag"] = options.tag
	}

	successPayload, resp, err := gateClient.V2PipelineTemplatesControllerApi.GetUsingGET2(gateClient.Context,
		id, queryParams)

	if err != nil {
		return err
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Encountered an error getting pipeline template with id %s, status code: %d\n",
			id,
			resp.StatusCode)
	}

	util.UI.JsonOutput(successPayload, util.UI.OutputFormat)
	return nil
}
