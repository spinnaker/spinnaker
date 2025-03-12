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

package output

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"k8s.io/client-go/util/jsonpath"
	"sigs.k8s.io/yaml"
)

type OutputFormater func(interface{}) ([]byte, error)

// ParseOutputFormat returns an OutputFormater based on the specified format.
// Accepted values include 'json', 'yaml', and 'jsonpath=PATH'.
// Empty string defaults to 'json'.
// For more about JSONPath, see https://goessner.net/articles/JsonPath/
func ParseOutputFormat(outputFormat string) (OutputFormater, error) {
	switch {
	case outputFormat == "" || outputFormat == "json":
		return MarshalToJson, nil
	case outputFormat == "yaml":
		return MarshalToYaml, nil
	case strings.HasPrefix(outputFormat, "jsonpath=") && outputFormat != "jsonpath=":
		toks := strings.Split(outputFormat, "=")
		if len(toks) != 2 {
			return nil, errors.New(fmt.Sprintf("Failed to parse output format flag value: %s", outputFormat))
		}
		return MarshalToJsonPathWrapper(toks[1]), nil
	default:
		return nil, errors.New(fmt.Sprintf("Failed to parse output format flag value: %s", outputFormat))
	}
}

func MarshalToJson(input interface{}) ([]byte, error) {
	pretty, err := json.MarshalIndent(input, "", " ")
	if err != nil {
		return nil, fmt.Errorf("Failed to marshal to json: %v", err)
	}
	return pretty, nil
}

// MarshalToJsonPathWrapper returns a MarshalToJsonPath function that uses the
// specified jsonpath expression.
// This leverages the kubernetes jsonpath library
// (https://kubernetes.io/docs/reference/kubectl/jsonpath/).
func MarshalToJsonPathWrapper(expression string) OutputFormater {
	expr := expression
	// aka MarshalToJsonPath
	return func(input interface{}) ([]byte, error) {
		jp := jsonpath.New("json-path")

		if err := jp.Parse(expr); err != nil {
			return nil, fmt.Errorf("Failed to parse jsonpath expression: %v", err)
		}

		values, err := jp.FindResults(input)
		if err != nil {
			return nil, fmt.Errorf("Failed to execute jsonpath %s on input %s: %v ", expr, input, err)
		}

		if values == nil || len(values) == 0 || len(values[0]) == 0 {
			return nil, errors.New(fmt.Sprintf("Error parsing value from input %v using template %s: %v ", input, expr, err))
		}

		json, err := MarshalToJson(values[0][0].Interface())
		if err != nil {
			return nil, err
		}
		return unquote(json), err
	}
}

func MarshalToYaml(input interface{}) ([]byte, error) {
	pretty, err := yaml.Marshal(input)
	if err != nil {
		return nil, fmt.Errorf("Failed to marshal to yaml: %v", err)
	}
	return pretty, nil
}

func unquote(input []byte) []byte {
	input = bytes.TrimLeft(input, "\"")
	input = bytes.TrimRight(input, "\"")
	return input
}
