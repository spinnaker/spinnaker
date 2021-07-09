'use strict';

import { module } from 'angular';

import { TaskMonitor } from '@spinnaker/core';

import { ScalingPolicyWriter } from '../ScalingPolicyWriter';
import { AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_ALARMCONFIGURER_COMPONENT } from './alarm/alarmConfigurer.component';
import { AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT } from './simple/simplePolicyAction.component';
import { STEP_POLICY_ACTION } from './step/stepPolicyAction.component';

import './upsertScalingPolicy.modal.less';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.upsertScalingPolicy.controller';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER, [
  AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT,
  STEP_POLICY_ACTION,
  AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_ALARM_ALARMCONFIGURER_COMPONENT,
]).controller('awsUpsertScalingPolicyCtrl', [
  '$uibModalInstance',
  'serverGroup',
  'application',
  'policy',
  function ($uibModalInstance, serverGroup, application, policy) {
    this.serverGroup = serverGroup;

    this.viewState = {
      isNew: !policy.policyARN,
      multipleAlarms: policy.alarms.length > 1,
      metricsLoaded: false,
      namespacesLoaded: false,
    };

    function createCommand() {
      return {
        name: policy.policyName,
        serverGroupName: serverGroup.name,
        credentials: serverGroup.account,
        region: serverGroup.region,
        provider: serverGroup.type,
        adjustmentType: policy.adjustmentType,
        minAdjustmentMagnitude: policy.minAdjustmentMagnitude || 1,
      };
    }

    function initializeAlarm(command, policy) {
      const alarm = policy.alarms[0];
      command.alarm = {
        name: alarm.alarmName,
        region: serverGroup.region,
        actionsEnabled: true,
        alarmDescription: alarm.alarmDescription,
        comparisonOperator: alarm.comparisonOperator,
        dimensions: alarm.dimensions,
        evaluationPeriods: alarm.evaluationPeriods,
        period: alarm.period,
        threshold: alarm.threshold,
        namespace: alarm.namespace,
        metricName: alarm.metricName,
        statistic: alarm.statistic,
        unit: alarm.unit,
        alarmActionArns: alarm.alarmActions,
        insufficientDataActionArns: alarm.insufficientDataActions,
        okActionArns: alarm.okActions,
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
        this.viewState.adjustmentType = policy.adjustmentType === 'ChangeInCapacity' ? 'instances' : 'percent of group';
      }

      if (policy.stepAdjustments && policy.stepAdjustments.length) {
        initializeStepPolicy(command, policy);
      } else {
        initializeSimplePolicy(command, policy);
      }

      this.command = command;
    };

    function initializeStepPolicy(command, policy) {
      const threshold = command.alarm.threshold;
      command.step = {
        estimatedInstanceWarmup: policy.estimatedInstanceWarmup || command.cooldown || 600,
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

    function initializeSimplePolicy(command, policy) {
      command.simple = {
        cooldown: policy.cooldown || 600,
        scalingAdjustment: Math.abs(policy.scalingAdjustment) || 1,
      };
    }

    this.boundsChanged = () => {
      const source = this.viewState.comparatorBound === 'min' ? 'metricIntervalLowerBound' : 'metricIntervalUpperBound';
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

    this.switchMode = () => {
      const command = this.command;
      const cooldownOrWarmup = command.step ? command.step.estimatedInstanceWarmup : command.simple.cooldown;
      if (command.step) {
        const policy = { cooldown: cooldownOrWarmup };
        delete command.step;
        initializeSimplePolicy(command, policy);
      } else {
        const stepAdjustments = [
          {
            scalingAdjustment: command.simple.scalingAdjustment,
          },
        ];
        if (this.viewState.comparatorBound === 'min') {
          stepAdjustments[0].metricIntervalUpperBound = 0;
        } else {
          stepAdjustments[0].metricIntervalLowerBound = 0;
        }
        delete command.simple;
        initializeStepPolicy(command, {
          estimatedInstanceWarmup: cooldownOrWarmup,
          stepAdjustments: stepAdjustments,
        });
        this.boundsChanged();
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
]);
