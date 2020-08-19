// Copyright (c) 2019, Google, Inc.
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

package orca_tasks

import (
	"fmt"
	"strings"
	"time"

	"github.com/spinnaker/spin/cmd/gateclient"
)

// WaitForSuccessfulTask observes an Orca task to see if it completed successfully.
func WaitForSuccessfulTask(gateClient *gateclient.GatewayClient, taskRef map[string]interface{}, maxAttempts int) error {
	id := idFromTaskRef(taskRef)
	task, resp, err := gateClient.TaskControllerApi.GetTaskUsingGET1(gateClient.Context, id)

	attempts := 0
	for (task == nil || !taskCompleted(task)) && attempts < maxAttempts {
		attempts += 1
		time.Sleep(time.Duration(attempts*attempts) * time.Second)
		id := idFromTaskRef(taskRef)
		task, resp, err = gateClient.TaskControllerApi.GetTaskUsingGET1(gateClient.Context, id)
	}

	if err != nil {
		return err
	}
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return fmt.Errorf("Encountered an error saving application, status code: %d\n", resp.StatusCode)
	}
	if !TaskSucceeded(task) {
		return fmt.Errorf("Encountered an error saving application, task output was: %v\n", task)
	}
	return nil
}

func taskCompleted(task map[string]interface{}) bool {
	taskStatus, exists := task["status"]
	if !exists {
		return false
	}
	COMPLETED := [...]string{"SUCCEEDED", "STOPPED", "SKIPPED", "TERMINAL", "FAILED_CONTINUE"}
	for _, status := range COMPLETED {
		if taskStatus == status {
			return true
		}
	}
	return false
}

func TaskSucceeded(task map[string]interface{}) bool {
	taskStatus, exists := task["status"]
	if !exists {
		return false
	}

	SUCCESSFUL := [...]string{"SUCCEEDED", "STOPPED", "SKIPPED"}
	for _, status := range SUCCESSFUL {
		if taskStatus == status {
			return true
		}
	}
	return false
}

func idFromTaskRef(taskRef map[string]interface{}) string {
	toks := strings.Split(taskRef["ref"].(string), "/")
	return toks[len(toks)-1]
}
