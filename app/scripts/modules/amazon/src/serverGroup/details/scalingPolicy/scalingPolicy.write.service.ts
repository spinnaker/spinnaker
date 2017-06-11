import { IPromise, module } from 'angular';

import {
  Application,
  IJob,
  IMetricAlarmDimension,
  IServerGroup,
  ITask,
  TASK_EXECUTOR,
  TaskExecutor
} from '@spinnaker/core';

import {
  AlarmComparisonOperator,
  AlarmStatisticType,
  IScalingPolicy,
  IStepAdjustment,
  MetricAggregationType,
  ScalingPolicyAdjustmentType,
  StandardUnitType
} from 'amazon/domain';

export interface IUpsertScalingPolicyCommand extends IJob {
  type?: string; // Orca/Clouddriver operation type
  cloudProvider: string;
  credentials: string;
  region: string;
  serverGroupName: string;
  adjustmentType: ScalingPolicyAdjustmentType;
  name?: string;
  minAdjustmentMagnitude?: number;
  alarm?: IUpsertAlarmDescription;
  simple?: ISimplePolicyDescription;
  step?: IStepPolicyDescription;
}

export interface ISimplePolicyDescription {
  cooldown: number;
  scalingAdjustment: number;
}

export interface IStepPolicyDescription {
  stepAdjustments: IStepAdjustment[];
  estimatedInstanceWarmup: number;
  metricAggregationType: MetricAggregationType;
}

export interface IConfigurableMetric {
  namespace: string,
  metricName: string,
  dimensions: IMetricAlarmDimension[],
}

export interface IUpsertAlarmDescription extends IConfigurableMetric {
  name: string;
  asgName: string;
  region: string;
  alarmDescription: string;
  comparisonOperator: AlarmComparisonOperator;
  evaluationPeriods: number;
  period: number;
  threshold: number;
  statistic: AlarmStatisticType;
  unit: StandardUnitType;
  alarmActionArns: string[];
  insufficientDataActionArns: string[];
  okActionArns: string[];
}

export class ScalingPolicyWriter {

  constructor(private taskExecutor: TaskExecutor) {
    'ngInject';
  }

  public upsertScalingPolicy(application: Application, command: IUpsertScalingPolicyCommand): IPromise<ITask> {
    command.type = command.type || 'upsertScalingPolicy';
    return this.taskExecutor.executeTask({
      application,
      description: 'Upsert scaling policy ' + (command.name || command.serverGroupName),
      job: [command]
    });
  }

  public deleteScalingPolicy(application: Application, serverGroup: IServerGroup, scalingPolicy: IScalingPolicy): IPromise<ITask> {
    return this.taskExecutor.executeTask({
      application,
      description: 'Delete scaling policy ' + scalingPolicy.policyName,
      job: [
        {
          type: 'deleteScalingPolicy',
          cloudProvider: 'aws',
          credentials: serverGroup.account,
          region: serverGroup.region,
          policyName: scalingPolicy.policyName,
          serverGroupName: serverGroup.name,
        }
      ]
    });
  }
}

export const SCALING_POLICY_WRITE_SERVICE = 'spinnaker.amazon.serverGroup.details.scalingPolicy.write.service';
module(SCALING_POLICY_WRITE_SERVICE, [
    TASK_EXECUTOR,
  ])
  .service('scalingPolicyWriter', ScalingPolicyWriter);
