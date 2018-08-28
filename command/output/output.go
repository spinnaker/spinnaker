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
	"errors"
	"fmt"
	"strings"
)

// TODO(jacobkiefer): Extend for other output formats.
type OutputFormat struct {
	// JsonPath specifies a subpath of the output to extract data from
	JsonPath string
}

func ParseOutputFormat(outputFormat string) (*OutputFormat, error) {
	format := new(OutputFormat)
	switch {
	case outputFormat == "":
		return format, nil
		break
	case strings.HasPrefix(outputFormat, "jsonpath="):
		toks := strings.Split(outputFormat, "=")
		if len(toks) != 2 {
			return nil, errors.New(fmt.Sprintf("Failed to parse output format flag value: %s", outputFormat))
		}
		format.JsonPath = toks[1]
		break
	default:
		return nil, errors.New(fmt.Sprintf("Failed to parse output format flag value: %s", outputFormat))
		break
	}
	return format, nil
}
