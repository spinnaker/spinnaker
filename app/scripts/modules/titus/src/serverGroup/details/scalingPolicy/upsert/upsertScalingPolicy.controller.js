'use strict';

import { module } from 'angular';

import { ScalingPolicyWriter } from '@spinnaker/amazon';
import { TaskMonitor } from '@spinnaker/core';

export const TITUS_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER =
  'spinnaker.titus.serverGroup.details.scalingPolicy.upsertScalingPolicy.controller';
export const name = TITUS_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER; // for backwards compatibility
module(TITUS_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER, []).controller(
  'titusUpsertScalingPolicyCtrl',
  [
    '$uibModalInstance',
    'alarmServerGroup',
    'serverGroup',
    'application',
    'policy',
    function ($uibModalInstance, alarmServerGroup, serverGroup, application, policy) {
      this.serverGroup = serverGroup;
      // alarmServerGroup is used to trick the chart rendering into using AWS metrics
      this.alarmServerGroup = alarmServerGroup;

      this.viewState = {
        isNew: !policy.id,
        multipleAlarms: policy.alarms.length > 1,
        metricsLoaded: false,
        namespacesLoaded: false,
      };

      function createCommand() {
        return {
          scalingPolicyID: policy.id,
          jobId: serverGroup.id,
          serverGroupName: serverGroup.name,
          credentials: serverGroup.account,
          region: serverGroup.region,
          cloudProvider: 'titus',
          adjustmentType: policy.adjustmentType,
          minAdjustmentMagnitude: policy.minAdjustmentMagnitude || 1,
        };
      }

      function initializeAlarm(command, policy) {
        const alarm = policy.alarms[0];
        command.alarm = {
          region: serverGroup.region,
          comparisonOperator: alarm.comparisonOperator,
          dimensions: alarm.dimensions,
          disableEditingDimensions: alarm.disableEditingDimensions,
          evaluationPeriods: alarm.evaluationPeriods,
          period: alarm.period,
          threshold: alarm.threshold,
          namespace: alarm.namespace,
          metricName: alarm.metricName,
          statistic: alarm.statistic,
          unit: alarm.unit,
        };
      }

      this.initialize = () => {
        const command = createCommand();

        initializeAlarm(command, policy);

        if (command.adjustmentType === 'ExactCapacity') {
          this.viewState.operator = 'Set to';
          this.viewState.adjustmentType = 'instances';
        } else {
          let adjustmentBasis = policy.scalingAdjustment;
          if (policy.stepAdjustments && policy.stepAdjustments.length) {
            adjustmentBasis = policy.stepAdjustments[0].scalingAdjustment;
          }
          this.viewState.operator = adjustmentBasis > 0 ? 'Add' : 'Remove';
          this.viewState.adjustmentType =
            policy.adjustmentType === 'ChangeInCapacity' ? 'instances' : 'percent of group';
        }

        initializeStepPolicy(command, policy);

        this.command = command;
      };

      function initializeStepPolicy(command, policy) {
        const threshold = command.alarm.threshold;
        command.step = {
          cooldown: policy.cooldown || 300,
          metricAggregationType: 'Average',
        };
        command.step.stepAdjustments = policy.stepAdjustments.map((adjustment) => {
          const step = {
            scalingAdjustment: Math.abs(adjustment.scalingAdjustment),
          };
          if (adjustment.metricIntervalUpperBound !== undefined) {
            step.metricIntervalUpperBound = adjustment.metricIntervalUpperBound + threshold;
          }
          if (adjustment.metricIntervalLowerBound !== undefined) {
            step.metricIntervalLowerBound = adjustment.metricIntervalLowerBound + threshold;
          }
          return step;
        });
      }

      this.boundsChanged = () => {
        const source =
          this.viewState.comparatorBound === 'min' ? 'metricIntervalLowerBound' : 'metricIntervalUpperBound';
        const target = source === 'metricIntervalLowerBound' ? 'metricIntervalUpperBound' : 'metricIntervalLowerBound';

        if (this.command.step) {
          const steps = this.command.step.stepAdjustments;
          steps.forEach((step, index) => {
            if (steps.length > index + 1) {
              steps[index + 1][target] = step[source];
            }
          });
          // remove the source boundary from the last step
          delete steps[steps.length - 1][source];
        }
      };

      this.action = this.viewState.isNew ? 'Create' : 'Edit';

      const prepareCommandForSubmit = () => {
        const command = _.cloneDeep(this.command);

        if (command.adjustmentType !== 'PercentChangeInCapacity') {
          delete command.minAdjustmentMagnitude;
        }

        if (command.step) {
          // adjust metricIntervalLowerBound/UpperBound for each step based on alarm threshold
          command.step.stepAdjustments.forEach((step) => {
            if (this.viewState.operator === 'Remove') {
              step.scalingAdjustment = 0 - step.scalingAdjustment;
              delete command.step.estimatedInstanceWarmup;
            }
            if (step.metricIntervalLowerBound !== undefined) {
              step.metricIntervalLowerBound -= command.alarm.threshold;
            }
            if (step.metricIntervalUpperBound !== undefined) {
              step.metricIntervalUpperBound -= command.alarm.threshold;
            }
          });
        } else {
          if (this.viewState.operator === 'Remove') {
            command.simple.scalingAdjustment = 0 - command.simple.scalingAdjustment;
          }
        }
        return command;
      };

      this.taskMonitor = new TaskMonitor({
        application: application,
        title: this.action + ' scaling policy for ' + serverGroup.name,
        modalInstance: $uibModalInstance,
      });

      this.save = () => {
        const command = prepareCommandForSubmit();
        const submitMethod = () => ScalingPolicyWriter.upsertScalingPolicy(application, command);

        this.taskMonitor.submit(submitMethod);
      };

      this.cancel = $uibModalInstance.dismiss;

      this.initialize();
    },
  ],
);
