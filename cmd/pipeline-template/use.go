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
	"io/ioutil"
	"os"
	"strings"

	"github.com/spf13/cobra"
	//"github.com/spinnaker/spin/cmd/output"
	"sigs.k8s.io/yaml"

	"github.com/spinnaker/spin/util"
)

type useOptions struct {
	*pipelineTemplateOptions
	id              string
	tag             string
	application     string
	name            string
	description     string
	variables       map[string]string
	templateType    string
	artifactAccount string
	variablesFiles  []string
}

const (
	usePipelineTemplateShort = "Creates a pipeline configuration using a managed pipeline template"
	usePipelineTemplateLong  = "Creates a pipeline configuration using a managed pipeline template"
)

func NewUseCmd(pipelineTemplateOptions *pipelineTemplateOptions) *cobra.Command {
	options := &useOptions{
		pipelineTemplateOptions: pipelineTemplateOptions,
	}
	cmd := &cobra.Command{
		Use:   "use",
		Short: usePipelineTemplateShort,
		Long:  usePipelineTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return usePipelineTemplate(cmd, options, args)
		},
	}

	cmd.PersistentFlags().StringVar(&options.id, "id", "", "id of the pipeline template")
	cmd.PersistentFlags().StringVarP(&options.application, "application", "a", "", "application to get the new pipeline")
	cmd.PersistentFlags().StringVarP(&options.name, "name", "n", "", "name of the new pipeline")
	cmd.PersistentFlags().StringVarP(&options.tag, "tag", "t", "", "(optional) specific tag to query")
	cmd.PersistentFlags().StringVarP(&options.description, "description", "d", "", "(optional) description of the pipeline")
	cmd.PersistentFlags().StringVar(&options.templateType, "type", "front50/pipelineTemplate", "(optional) template type")
	cmd.PersistentFlags().StringVar(&options.artifactAccount, "artifact-account", "front50ArtifactCredentials", "(optional) artifact account")
	cmd.PersistentFlags().StringToStringVar(&options.variables, "set", nil, "template variables/values required by the template.  Format: key=val,key1=val1")
	cmd.PersistentFlags().StringSliceVar(&options.variablesFiles, "values", nil, "json/yaml files with template variables and values")

	return cmd
}

func usePipelineTemplate(cmd *cobra.Command, options *useOptions, args []string) error {
	id, errID := getTemplateID(options, args)
	if errID != nil {
		return errID
	}

	// Check required params
	options.application = strings.TrimSpace(options.application)
	if options.application == "" {
		return errors.New("no application name supplied, exiting")
	}
	options.name = strings.TrimSpace(options.name)
	if options.name == "" {
		return errors.New("no pipeline name supplied, exiting")
	}

	// Build pipeline using template, output
	pipeline, err := buildUsingTemplate(id, options)
	if err != nil {
		return err
	}

	options.Ui.JsonOutput(pipeline)

	return nil
}

func getTemplateID(options *useOptions, args []string) (string, error) {
	// Check options if they passed in like --id
	optionsID := strings.TrimSpace(options.id)
	if optionsID != "" {
		return optionsID, nil
	}
	// Otherwise get from arguments
	argsID, err := util.ReadArgsOrStdin(args)
	if err != nil {
		return "", err
	}
	argsID = strings.TrimSpace(argsID)
	if argsID == "" {
		return "", errors.New("no pipeline template id supplied, exiting")
	}

	return argsID, nil
}

func buildUsingTemplate(id string, options *useOptions) (map[string]interface{}, error) {
	pipeline := make(map[string]interface{})

	// get variables from cmd and files
	variables, err := getVariables(options)
	if err != nil {
		return nil, err
	}

	// Configure pipeline.template
	templateProperty := map[string]string{
		"artifactAccount": options.artifactAccount,
		"type":            options.templateType,
		"reference":       getFullTemplateID(id, options.tag),
	}

	// Configure pipeline
	pipeline["template"] = templateProperty
	pipeline["schema"] = "v2"
	pipeline["application"] = options.application
	pipeline["name"] = options.name
	pipeline["description"] = options.description
	pipeline["variables"] = variables

	// Properties not supported by spin, add empty default values which can be populated manually if desired
	pipeline["exclude"] = make([]string, 0)
	pipeline["triggers"] = make([]string, 0)
	pipeline["parameters"] = make([]string, 0)
	pipeline["notifications"] = make([]string, 0)
	pipeline["stages"] = make([]string, 0)

	return pipeline, nil
}

func getVariables(options *useOptions) (map[string]string, error) {
	// Create map for variables
	var variables map[string]string

	if len(options.variablesFiles) == 0 {
		variables = make(map[string]string)
	} else {
		fileVars, err := parseKeyValsFromFile(options.variablesFiles, false)
		if err != nil {
			return nil, err
		}
		variables = fileVars
	}

	// Merge maps, with vars from command line overriding file vars
	if len(options.variables) > 0 {
		for k, v := range options.variables {
			variables[k] = v
		}
	}

	// return all variables from file and command line
	return variables, nil
}

func getFullTemplateID(id string, tag string) string {
	// If no protocol given, add default spinnaker://
	if !strings.Contains(id, "://") {
		id = fmt.Sprintf("spinnaker://%s", id)
	}
	// Append the tag if they set one
	if tag != "" {
		id = fmt.Sprintf("%s:%s", id, tag)
	}
	// Otherwise they have set the protocol, return it back as is
	return id
}

func parseKeyValsFromFile(filePaths []string, tolerateEmptyInput bool) (map[string]string, error) {
	var fromFile *os.File
	var err error
	var variables map[string]string

	for _, filePath := range filePaths {
		if filePath == "" {
			err = nil
			if !tolerateEmptyInput {
				err = errors.New("No file path given")
			}
			return nil, err
		}

		fromFile, err = os.Open(filePath)
		if err != nil {
			return nil, err
		}

		fi, err := fromFile.Stat()
		if err != nil {
			return nil, err
		}

		if fi.Size() <= 0 {
			err = nil
			if !tolerateEmptyInput {
				err = errors.New("No json or yaml input to parse")
			}
			return nil, err
		}

		byteValue, err := ioutil.ReadAll(fromFile)
		if err != nil {
			return nil, fmt.Errorf("Failed to read file: %v", err)
		}

		err = yaml.UnmarshalStrict(byteValue, &variables)
		if err != nil {
			return nil, fmt.Errorf("Failed to unmarshal: %v", err)
		}
	}

	return variables, nil
}
