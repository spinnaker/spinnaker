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
	"io/ioutil"
	"net/http"
	"strings"

	"github.com/spf13/cobra"

	"github.com/spinnaker/spinnaker/spin/util"
)

type saveOptions struct {
	*PipelineOptions
	output       string
	pipelineFile string
	staleCheck   bool
}

var (
	savePipelineShort = "Save the provided pipeline"
	savePipelineLong  = "Save the provided pipeline"
)

func NewSaveCmd(pipelineOptions *PipelineOptions) *cobra.Command {
	options := &saveOptions{
		PipelineOptions: pipelineOptions,
	}
	cmd := &cobra.Command{
		Use:     "save",
		Aliases: []string{},
		Short:   savePipelineShort,
		Long:    savePipelineLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return savePipeline(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.pipelineFile, "file", "f", "", "path to the pipeline file")
	cmd.PersistentFlags().BoolVar(&options.staleCheck, "stale-check", true,
		"fail instead of overwriting if the pipeline changed in Spinnaker after this command read it")

	return cmd
}

func savePipeline(cmd *cobra.Command, options *saveOptions) error {
	pipelineJson, err := util.ParseJsonFromFileOrStdin(options.pipelineFile, false)
	if err != nil {
		return err
	}
	valid := true
	if _, exists := pipelineJson["name"]; !exists {
		options.Ui.Error("Required pipeline key 'name' missing...\n")
		valid = false
	}

	if _, exists := pipelineJson["application"]; !exists {
		options.Ui.Error("Required pipeline key 'application' missing...\n")
		valid = false
	}

	if template, exists := pipelineJson["template"]; exists && len(template.(map[string]interface{})) > 0 {
		if _, exists := pipelineJson["schema"]; !exists {
			options.Ui.Error("Required pipeline key 'schema' missing for templated pipeline...\n")
			valid = false
		}
		pipelineJson["type"] = "templatedPipeline"
	}

	if !valid {
		return fmt.Errorf("Submitted pipeline is invalid: %s\n", pipelineJson)
	}

	application := pipelineJson["application"].(string)
	pipelineName := pipelineJson["name"].(string)

	// updateTs is server-owned. A value carried in the user's input file (e.g. a committed
	// export) must never become a precondition -- only a value we just fetched may be.
	delete(pipelineJson, "updateTs")

	foundPipeline, queryResp, queryErr := options.GateClient.ApplicationControllerAPI.
		GetPipelineConfig(options.GateClient.Context, application, pipelineName).Execute()
	if queryResp == nil {
		return fmt.Errorf("failed to look up pipeline %q in application %q: %w", pipelineName, application, queryErr)
	}
	defer queryResp.Body.Close()

	switch queryResp.StatusCode {
	case http.StatusOK:
		// pipeline found, let's use Spinnaker's known Pipeline ID, otherwise we'll get one created for us
		if len(foundPipeline) > 0 {
			pipelineJson["id"] = foundPipeline["id"].(string)

			// Carry the server's fingerprint forward so front50 can reject the write if
			// someone else changed the pipeline inside our read -> write window.
			if options.staleCheck {
				if updateTs, present := foundPipeline["updateTs"]; present && updateTs != nil {
					pipelineJson["updateTs"] = updateTs
				}
			}
		}
	case http.StatusNotFound:
		// pipeline doesn't exists, let's create a new one
	default:
		b, _ := ioutil.ReadAll(queryResp.Body)
		return fmt.Errorf("unhandled response %d: %s", queryResp.StatusCode, b)
	}

	saveReq := options.GateClient.PipelineControllerAPI.SavePipeline(options.GateClient.Context).RequestBody(pipelineJson)
	if options.staleCheck {
		saveReq = saveReq.StaleCheck(true)
	}

	saveResp, err := saveReq.Execute()
	if err != nil {
		return savePipelineError(application, pipelineName, saveResp, err)
	}
	if saveResp.StatusCode != http.StatusOK {
		return savePipelineError(application, pipelineName, saveResp, nil)
	}

	options.Ui.Success("Pipeline save succeeded")
	return nil
}

// savePipelineError turns a failed save into something actionable, special-casing
// front50's stale-pipeline rejection.
func savePipelineError(application, name string, resp *http.Response, err error) error {
	body := ""
	// The generated client drains the response body into its own error type, so prefer
	// that when it's present.
	var apiErr interface{ Body() []byte }
	if err != nil && errors.As(err, &apiErr) {
		body = string(apiErr.Body())
	} else if resp != nil && resp.Body != nil {
		if b, readErr := ioutil.ReadAll(resp.Body); readErr == nil {
			body = string(b)
		}
	}

	if strings.Contains(body, "is stale") {
		return fmt.Errorf(
			"pipeline %q in application %q was modified in Spinnaker while this command was running; nothing was saved.\n"+
				"Re-run to pick up the change, or pass --stale-check=false to overwrite it.\n"+
				"Server said: %s",
			name, application, strings.TrimSpace(body))
	}

	if err != nil {
		return fmt.Errorf("encountered an error saving pipeline %q: %w", name, err)
	}
	status := 0
	if resp != nil {
		status = resp.StatusCode
	}
	return fmt.Errorf("encountered an error saving pipeline %q, status code: %d\n%s", name, status, strings.TrimSpace(body))
}
