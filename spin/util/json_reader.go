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

package util

import (
	"errors"
	"fmt"
	"io/ioutil"
	"os"

	"sigs.k8s.io/yaml"
)

func ParseJsonFromFileOrStdin(filePath string, tolerateEmptyStdin bool) (map[string]interface{}, error) {
	var fromFile *os.File
	var err error
	var jsonContent map[string]interface{}

	if filePath != "" {
		fromFile, err = os.Open(filePath)
		if err != nil {
			return nil, err
		}
	} else {
		fromFile = os.Stdin
	}

	fi, err := fromFile.Stat()
	if err != nil {
		return nil, err
	}

	pipedStdin := (fi.Mode() & os.ModeCharDevice) == 0
	if fi.Size() <= 0 && !pipedStdin {
		err = nil
		if !tolerateEmptyStdin {
			err = errors.New("No json input to parse")
		}
		return nil, err
	}

	byteValue, err := ioutil.ReadAll(fromFile)
	if err != nil {
		return nil, fmt.Errorf("Failed to read file: %v", err)
	}

	err = yaml.UnmarshalStrict(byteValue, &jsonContent)
	if err != nil {
		return nil, fmt.Errorf("Failed to unmarshal: %v", err)
	}
	return jsonContent, nil
}

func ParseJsonFromFile(filePath string, tolerateEmptyInput bool) (map[string]interface{}, error) {
	var fromFile *os.File
	var err error
	var jsonContent map[string]interface{}

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
			err = errors.New("No json input to parse")
		}
		return nil, err
	}

	byteValue, err := ioutil.ReadAll(fromFile)
	if err != nil {
		return nil, fmt.Errorf("Failed to read file: %v", err)
	}

	err = yaml.UnmarshalStrict(byteValue, &jsonContent)
	if err != nil {
		return nil, fmt.Errorf("Failed to unmarshal: %v", err)
	}
	return jsonContent, nil
}
