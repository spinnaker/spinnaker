import { cloneDeep } from 'lodash';

import type {
  ISimplePolicyDescription,
  IStepPolicyDescription,
  IUpsertAlarmDescription,
  IUpsertScalingPolicyCommand,
} from '../ScalingPolicyWriter';
import type {
  IAmazonServerGroup,
  IScalingPolicy,
  IScalingPolicyAlarm,
  IStepAdjustment,
  ITargetTrackingPolicy,
} from '../../../../domain';

type PolicyType = 'Step' | 'TargetTracking';

export const ScalingPolicyCommandBuilder = {
  buildAlarm: (policy: IScalingPolicy, region: string, asgName: string): IUpsertAlarmDescription => {
    const alarm = policy?.alarms ? policy.alarms[0] : ({} as IScalingPolicyAlarm);
    return {
      name: alarm.alarmName,
      actionsEnabled: true,
      alarmActionArns: alarm.alarmActions,
      alarmDescription: alarm.alarmDescription,
      asgName: asgName,
      comparisonOperator: alarm.comparisonOperator,
      dimensions: alarm.dimensions,
      evaluationPeriods: alarm.evaluationPeriods,
      insufficientDataActionArns: alarm.insufficientDataActions,
      metricName: alarm.metricName,
      namespace: alarm.namespace,
      okActionArns: alarm.okactions,
      period: alarm.period,
      region,
      statistic: alarm.statistic,
      threshold: alarm.threshold,
      unit: alarm.unit,
    };
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
      estimatedInstanceWarmup: policy.estimatedInstanceWarmup ?? cooldown ?? 600,
      metricAggregationType: 'Average',
      stepAdjustments,
    };
  },

  buildSimplePolicy: (policy: IScalingPolicy): ISimplePolicyDescription => ({
    cooldown: policy.cooldown ?? 600,
    scalingAdjustment: Math.abs(policy.scalingAdjustment) ?? 1,
  }),

  buildNewCommand: (
    type: PolicyType,
    serverGroup: IAmazonServerGroup,
    policy: ITargetTrackingPolicy,
  ): IUpsertScalingPolicyCommand => {
    const command = {
      name: policy.policyName,
      adjustmentType: type === 'Step' ? policy.adjustmentType : null,
      cloudProvider: serverGroup.cloudProvider,
      credentials: serverGroup.account,
      provider: serverGroup.type,
      region: serverGroup.region,
      serverGroupName: serverGroup.name,
    } as IUpsertScalingPolicyCommand;

    if (type === 'Step') {
      command.alarm = ScalingPolicyCommandBuilder.buildAlarm(policy, serverGroup.region, serverGroup.name);
      command.minAdjustmentMagnitude = policy.minAdjustmentMagnitude ?? 1;

      if (policy.stepAdjustments?.length) {
        command.step = ScalingPolicyCommandBuilder.buildStepPolicy(policy, command.alarm.threshold, command.cooldown);
      } else {
        command.simple = ScalingPolicyCommandBuilder.buildSimplePolicy(policy);
      }
    }

    if (type === 'TargetTracking') {
      command.estimatedInstanceWarmup = policy.estimatedInstanceWarmup ?? 600;
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
