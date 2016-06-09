'use strict';

const angular = require('angular');

require('./upsertScalingPolicy.modal.less');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.scalingPolicy.upsertScalingPolicy.controller', [
    require('../scalingPolicy.write.service.js'),
    require('exports?"n3-line-chart"!n3-charts/build/LineChart.js'),
    require('../../../../../core/serverGroup/serverGroup.read.service.js'),
    require('./simple/simplePolicyAction.component.js'),
    require('./step/stepPolicyAction.component.js'),
    require('./alarm/alarmConfigurer.component.js'),
  ])
  .controller('awsUpsertScalingPolicyCtrl', function ($uibModalInstance, _, scalingPolicyWriter,
                                                      taskMonitorService,
                                                      serverGroupReader, serverGroup, application, policy) {

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
      let alarm = policy.alarms[0];
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
        okActionArns: alarm.okActions
      };
    }

    this.initialize = () => {
      var command = createCommand();

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
      let threshold = command.alarm.threshold;
      command.step = {
        estimatedInstanceWarmup: policy.estimatedInstanceWarmup || command.cooldown || 600,
        metricAggregationType: 'Average',
      };
      command.step.stepAdjustments = policy.stepAdjustments.map((adjustment) => {
        let step = {
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
      let source = this.viewState.comparatorBound === 'min' ? 'metricIntervalLowerBound' : 'metricIntervalUpperBound',
          target = source === 'metricIntervalLowerBound' ? 'metricIntervalUpperBound' : 'metricIntervalLowerBound';

      if (this.command.step) {
        let steps = this.command.step.stepAdjustments;
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
      let command = this.command;
      let cooldownOrWarmup = command.step ? command.step.estimatedInstanceWarmup : command.simple.cooldown;
      if (command.step) {
        let policy = { cooldown: cooldownOrWarmup };
        delete command.step;
        initializeSimplePolicy(command, policy);
      } else {
        let stepAdjustments = [{
          scalingAdjustment: command.simple.scalingAdjustment
        }];
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

    let prepareCommandForSubmit = () => {
      let command = _.cloneDeep(this.command);

      if (command.adjustmentType !== 'PercentChangeInCapacity') {
        delete command.minAdjustmentMagnitude;
      }

      if (command.step) {
        // adjust metricIntervalLowerBound/UpperBound for each step based on alarm threshold
        command.step.stepAdjustments.forEach((step) => {
          if (this.viewState.operator === 'Remove') {
            step.scalingAdjustment = 0 - step.scalingAdjustment;
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

    this.save = () => {
      let command = prepareCommandForSubmit();
      var submitMethod = () => scalingPolicyWriter.upsertScalingPolicy(application, command);

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: this.action + ' scaling policy for ' + serverGroup.name,
      };

      this.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      this.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;

    this.initialize();
  });
