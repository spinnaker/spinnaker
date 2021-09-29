import { cloneDeep } from 'lodash';

import type {
  IScalingPolicy,
  IScalingPolicyAlarmView,
  IStepAdjustment,
  IStepPolicyDescription,
  ITargetTrackingPolicy,
  IUpsertAlarmDescription,
  IUpsertScalingPolicyCommand,
} from '@spinnaker/amazon';
import type { ITitusServerGroup } from '../../../../domain';

type PolicyType = 'Step' | 'TargetTracking';

export const TitusScalingPolicyCommandBuilder = {
  buildAlarm: (policy: IScalingPolicy, region: string): IUpsertAlarmDescription => {
    const alarm = policy?.alarms[0] as IScalingPolicyAlarmView;
    return {
      comparisonOperator: alarm.comparisonOperator,
      region,
      dimensions: alarm.dimensions,
      disableEditingDimensions: alarm.disableEditingDimensions,
      evaluationPeriods: alarm.evaluationPeriods,
      metricName: alarm.metricName,
      namespace: alarm.namespace,
      period: alarm.period,
      statistic: alarm.statistic,
      threshold: alarm.threshold,
      unit: alarm.unit,
    } as IUpsertAlarmDescription;
  },

  buildStepPolicy: (policy: IScalingPolicy, threshold: number, cooldown: number): IStepPolicyDescription => {
    const stepAdjustments = policy.stepAdjustments.map((adjustment) => {
      const step = {
        scalingAdjustment: Math.abs(adjustment.scalingAdjustment),
      } as IStepAdjustment;

      if (adjustment.metricIntervalUpperBound !== undefined) {
        step.metricIntervalUpperBound = adjustment.metricIntervalUpperBound + threshold;
      }
      if (adjustment.metricIntervalLowerBound !== undefined) {
        step.metricIntervalLowerBound = adjustment.metricIntervalLowerBound + threshold;
      }

      return step;
    });

    return {
      cooldown: cooldown || 600,
      estimatedInstanceWarmup: null,
      metricAggregationType: 'Average',
      stepAdjustments,
    };
  },

  buildNewCommand: (
    type: PolicyType,
    serverGroup: ITitusServerGroup,
    policy: ITargetTrackingPolicy,
  ): IUpsertScalingPolicyCommand => {
    const command = {
      adjustmentType: type === 'Step' ? policy.adjustmentType : null,
      cloudProvider: serverGroup.cloudProvider,
      credentials: serverGroup.account,
      jobId: serverGroup.id,
      name: policy.id,
      region: serverGroup.region,
      scalingPolicyID: policy.id,
      serverGroupName: serverGroup.name,
    } as IUpsertScalingPolicyCommand;

    if (type === 'Step') {
      command.alarm = TitusScalingPolicyCommandBuilder.buildAlarm(policy, serverGroup.region);
      command.minAdjustmentMagnitude = policy.minAdjustmentMagnitude || 1;
      command.step = TitusScalingPolicyCommandBuilder.buildStepPolicy(policy, command.alarm.threshold, policy.cooldown);
    }

    if (type === 'TargetTracking') {
      command.targetTrackingConfiguration = { ...policy.targetTrackingConfiguration };
    }

    return command;
  },

  prepareCommandForUpsert: (command: IUpsertScalingPolicyCommand, isRemove: boolean): IUpsertScalingPolicyCommand => {
    const commandToSubmit = cloneDeep(command);

    if (commandToSubmit.adjustmentType !== 'PercentChangeInCapacity') {
      delete commandToSubmit.minAdjustmentMagnitude;
    }

    if (commandToSubmit.step) {
      // adjust metricIntervalLowerBound/UpperBound for each step based on alarm threshold
      commandToSubmit.step.stepAdjustments.forEach((step) => {
        if (isRemove) {
          step.scalingAdjustment = 0 - step.scalingAdjustment;
          delete commandToSubmit.step.estimatedInstanceWarmup;
        }
        if (step.metricIntervalLowerBound !== undefined) {
          step.metricIntervalLowerBound -= commandToSubmit.alarm.threshold;
        }
        if (step.metricIntervalUpperBound !== undefined) {
          step.metricIntervalUpperBound -= commandToSubmit.alarm.threshold;
        }
      });
    } else {
      if (isRemove) {
        command.simple.scalingAdjustment = 0 - command.simple.scalingAdjustment;
      }
    }
    return commandToSubmit;
  },
};
