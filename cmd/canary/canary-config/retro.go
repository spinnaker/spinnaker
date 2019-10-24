// Copyright (c) 2019, Waze, Inc.
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

package canary_config

import (
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/spinnaker/spin/cmd/gateclient"
	"github.com/spinnaker/spin/util"
	"net/http"
	"strings"
	"time"
)

type RetroOptions struct {
	*canaryConfigOptions
	output              string
	canaryConfigFile    string
	controlGroup        string
	controlLocation     string
	experimentGroup     string
	experimentLocation  string
	startInstant        string
	endInstant          string
	extendedScopeParams map[string]string
	metricsAccount      string
	storageAccount      string
	stepSize            int
	marginalScore       int
	passScore           int
	fullResult          bool
}

const (
	retroTemplateShort = "Retro the provided canary config"
	retroTemplateLong  = "Retro the provided canary config"
)

var (
	retrySleepCycle = 6 * time.Second
)

func NewRetroCmd(canaryConfigOptions canaryConfigOptions) *cobra.Command {
	options := RetroOptions{
		canaryConfigOptions: &canaryConfigOptions,
	}
	cmd := &cobra.Command{
		Use:     "retro",
		Aliases: []string{},
		Short:   retroTemplateShort,
		Long:    retroTemplateLong,
		RunE: func(cmd *cobra.Command, args []string) error {
			return retroCanaryConfig(cmd, options)
		},
	}

	cmd.PersistentFlags().StringVarP(&options.canaryConfigFile, "file",
		"f", "", "path to the canary config file")
	cmd.PersistentFlags().StringVar(&options.controlGroup, "control-group", "", "Control server group name (required)")
	cmd.PersistentFlags().StringVar(&options.controlLocation, "control-location", "", "Control server group location (required)")
	cmd.PersistentFlags().StringVar(&options.experimentGroup, "experiment-group", "", "Experiment server group name (required)")
	cmd.PersistentFlags().StringVar(&options.experimentLocation, "experiment-location", "", "Experiment server group location (required)")
	cmd.PersistentFlags().StringVar(&options.startInstant, "start", "", "Start of canary window, in ISO Instant format (required)")
	cmd.PersistentFlags().StringVar(&options.endInstant, "end", "", "End of canary window, in ISO Instant format (required)")

	cmd.PersistentFlags().IntVar(&options.stepSize, "step", 10, "Canary sampling step size in seconds")
	cmd.PersistentFlags().IntVar(&options.marginalScore, "marginal-score", 75, "Canary marginal score threshold")
	cmd.PersistentFlags().IntVar(&options.passScore, "pass-score", 95, "Canary pass score threshold")
	cmd.PersistentFlags().StringToStringVar(&options.extendedScopeParams, "extended-scope-params", nil, "Extended scope params for retrospective")
	cmd.PersistentFlags().StringVar(&options.metricsAccount, "metrics-account", "", "Metrics account to use in the retrospective")
	cmd.PersistentFlags().StringVar(&options.storageAccount, "storage-account", "", "Storage account to use in the retrospective")

	cmd.PersistentFlags().BoolVar(&options.fullResult, "full-result", false, "Whether to print the full canary result")

	return cmd
}

func retroCanaryConfig(cmd *cobra.Command, options RetroOptions) error {
	gateClient, err := gateclient.NewGateClient(cmd.InheritedFlags())
	if err != nil {
		return err
	}

	canaryConfigJson, err := util.ParseJsonFromFileOrStdin(options.canaryConfigFile, false)
	if err != nil {
		return err
	}

	validateErr := validateOptions(options)
	if validateErr != nil {
		return validateErr
	}

	startTime, tErr := time.Parse(time.RFC3339, options.startInstant)
	if tErr != nil {
		return tErr
	}
	endTime, tErr := time.Parse(time.RFC3339, options.endInstant)
	if tErr != nil {
		return tErr
	}

	scopes := map[string]interface{}{
		"default": map[string]interface{}{
			"controlScope": map[string]interface{}{
				"scope":               options.controlGroup,
				"location":            options.controlLocation,
				"start":               startTime,
				"end":                 endTime,
				"step":                options.stepSize,
				"extendedScopeParams": options.extendedScopeParams,
			},
			"experimentScope": map[string]interface{}{
				"scope":               options.experimentGroup,
				"location":            options.experimentLocation,
				"start":               startTime,
				"end":                 endTime,
				"step":                options.stepSize,
				"extendedScopeParams": options.extendedScopeParams,
			},
		},
	}

	executionRequest := map[string]interface{}{
		"scopes": scopes,
		"thresholds": map[string]int{
			"pass":     options.passScore,
			"marginal": options.marginalScore,
		},
	}

	adhocRequest := map[string]interface{}{
		"canaryConfig":     canaryConfigJson,
		"executionRequest": executionRequest,
	}

	initiateOptionalParams := map[string]interface{}{}
	if options.metricsAccount != "" {
		initiateOptionalParams["metricsAccountName"] = options.metricsAccount
	}
	if options.storageAccount != "" {
		initiateOptionalParams["storageAccountName"] = options.storageAccount
	}

	util.UI.Info("Initiating canary execution for supplied canary config")
	canaryExecutionResp, initiateResp, initiateErr := gateClient.V2CanaryControllerApi.InitiateCanaryWithConfigUsingPOST(gateClient.Context, adhocRequest, initiateOptionalParams)

	if initiateErr != nil {
		return initiateErr
	}

	if initiateResp.StatusCode != http.StatusOK {
		return fmt.Errorf(
			"Encountered an unexpected status code %d initiating execution for canary config\n",
			initiateResp.StatusCode)
	}

	canaryExecutionId := canaryExecutionResp.(map[string]interface{})["canaryExecutionId"].(string)
	util.UI.Info(fmt.Sprintf("Spawned canary execution with id %s, polling for completion...", canaryExecutionId))

	queryOptionalParams := map[string]interface{}{}
	if options.storageAccount != "" {
		queryOptionalParams["storageAccountName"] = options.storageAccount
	}

	canaryResult, canaryResultResp, canaryResultErr := gateClient.V2CanaryControllerApi.GetCanaryResultUsingGET1(gateClient.Context, canaryExecutionId, queryOptionalParams)

	if canaryResultErr != nil {
		return canaryResultErr
	}

	if canaryResultResp.StatusCode != http.StatusOK {
		return fmt.Errorf(
			"Encountered an unexpected status code %d querying canary execution with id: %s\n",
			canaryResultResp.StatusCode, canaryExecutionId)
	}

	complete := canaryResult.(map[string]interface{})["complete"].(bool)

	retries := 0
	for retries < 10 && complete == false && canaryResultErr == nil {
		canaryResult, canaryResultResp, canaryResultErr = gateClient.V2CanaryControllerApi.GetCanaryResultUsingGET1(gateClient.Context, canaryExecutionId, queryOptionalParams)
		complete = canaryResult.(map[string]interface{})["complete"].(bool)
		time.Sleep(retrySleepCycle)
		retries += 1
	}

	if canaryResultErr != nil {
		return canaryResultErr
	} else if complete == false {
		return fmt.Errorf(
			"Canary execution %s incomplete after 60 seconds, aborting", canaryExecutionId)
	}

	judgement := canaryResult.(map[string]interface{})["result"].(map[string]interface{})["judgeResult"].(map[string]interface{})["score"].(map[string]interface{})["classification"].(string)

	util.UI.Info(util.Colorize().Color(fmt.Sprintf("Retrospective canary execution finished, judgement = %s", strings.ToUpper(judgement))))
	if options.fullResult {
		util.UI.JsonOutput(canaryResult, util.UI.OutputFormat)
	}
	return nil
}

func validateOptions(options RetroOptions) error {
	if options.controlGroup == "" || options.controlLocation == "" {
		return errors.New("Required control group flags not supplied")
	}

	if options.experimentGroup == "" || options.experimentLocation == "" {
		return errors.New("Required experiment group flags not supplied")
	}

	if options.startInstant == "" || options.endInstant == "" {
		return errors.New("Required time interval flags not supplied")
	}
	return nil
}
