import { cloneDeep, set } from 'lodash';
import * as React from 'react';

import { NumberInput, ReactSelectInput } from '@spinnaker/core';
import type { Application, ILoadBalancer } from '@spinnaker/core';

import type { ITargetTrackingPolicyCommand } from '../ScalingPolicyWriter';
import { TargetTrackingChart } from './TargetTrackingChart';
import type {
  IAmazonApplicationLoadBalancer,
  IAmazonServerGroup,
  ICustomizedMetricSpecification,
  IScalingPolicyAlarmView,
  ITargetGroup,
} from '../../../../domain';
import { MetricSelector } from '../upsert/alarm/MetricSelector';

import './TargetMetricFields.less';

export type MetricType = 'custom' | 'predefined';
export interface ITargetMetricFieldsProps {
  allowDualMode?: boolean;
  cloudwatch?: boolean;
  command: ITargetTrackingPolicyCommand;
  isCustomMetric: boolean;
  app: Application;
  serverGroup: IAmazonServerGroup;
  toggleMetricType?: (type: MetricType) => void;
  updateCommand: (command: ITargetTrackingPolicyCommand) => void;
}

interface IalbArn {
  loadBalancerArn: string;
}

interface ItargetGroupArn {
  targetGroupArn: string;
}

export const TargetMetricFields = ({
  allowDualMode,
  cloudwatch,
  command,
  isCustomMetric,
  app,
  serverGroup,
  toggleMetricType,
  updateCommand,
}: ITargetMetricFieldsProps) => {
  const predefinedMetrics = [
    'ASGAverageCPUUtilization',
    'ASGAverageNetworkOut',
    'ASGAverageNetworkIn',
    'ALBRequestCountPerTarget',
  ];
  const statistics = ['Average', 'Maximum', 'Minimum', 'SampleCount', 'Sum'];
  const [unit, setUnit] = React.useState<string>(null);

  const setCommandField = (path: string, value: any) => {
    const newCommand = cloneDeep(command);
    set(newCommand, path, value);
    updateCommand(newCommand);
  };

  const updateAlarm = (newAlarm: ICustomizedMetricSpecification) => {
    setCommandField('targetTrackingConfiguration.customizedMetricSpecification', newAlarm);
  };

  const onMetricTypeChange = () => {
    const newCommand = cloneDeep(command);
    if (isCustomMetric) {
      set(newCommand, 'targetTrackingConfiguration.predefinedMetricSpecification', {
        predefinedMetricType: 'ASGAverageCPUUtilization',
      });
      set(newCommand, 'targetTrackingConfiguration.customizedMetricSpecification', null);
    } else {
      set(newCommand, 'targetTrackingConfiguration.predefinedMetricSpecification', null);
      set(newCommand, 'targetTrackingConfiguration.customizedMetricSpecification', {
        metricName: 'CPUUtilization',
        namespace: 'AWS/EC2',
        dimensions: [{ name: 'AutoScalingGroupName', value: serverGroup.name }],
        statistic: 'Average',
      });
    }

    updateCommand(newCommand);
    toggleMetricType(isCustomMetric ? 'predefined' : 'custom');
  };

  const targetGroupOptions = () => {
    const loadBalancers = app.loadBalancers.data as ILoadBalancer[];
    const albs = loadBalancers.filter(
      (lb) => lb.account === serverGroup.account && lb.region === serverGroup.region,
    ) as Array<IAmazonApplicationLoadBalancer & IalbArn>;
    const targetGroups = albs.flatMap((alb) =>
      alb.targetGroups
        .filter((tg) => serverGroup.targetGroups.some((serverGroupTg) => serverGroupTg === tg.name))
        .map((tg) => ({ ...tg, loadBalancerArn: alb.loadBalancerArn })),
    ) as Array<ITargetGroup & IalbArn & ItargetGroupArn>;
    return targetGroups.map((tg) => ({
      label: tg.name,
      value: `${tg.loadBalancerArn.substring(tg.loadBalancerArn.indexOf('app'))}/${tg.targetGroupArn.substring(
        tg.targetGroupArn.indexOf('targetgroup'),
      )}`,
    }));
  };

  return (
    <div className="TargetMetricFields sp-margin-l-xaxis">
      <p>
        With target tracking policies, Amazon will automatically adjust the size of your ASG to keep the selected metric
        as close as possible to the selected value.
      </p>
      {cloudwatch && (
        <p>
          <b>Note:</b> metrics must be sent to Amazon CloudWatch before they can be used in auto scaling. If you do not
          see a metric below, click "Configure available metrics" in the server group details to set up forwarding from
          Atlas to CloudWatch.
        </p>
      )}
      <div className="row sp-margin-s-yaxis">
        <div className="col-md-2 sm-label-right">Metric</div>
        <div className="col-md-10 content-fields">
          {!isCustomMetric && (
            <ReactSelectInput
              value={command.targetTrackingConfiguration.predefinedMetricSpecification?.predefinedMetricType}
              stringOptions={predefinedMetrics}
              onChange={(e) =>
                setCommandField(
                  'targetTrackingConfiguration.predefinedMetricSpecification.predefinedMetricType',
                  e.target.value,
                )
              }
              inputClassName="metric-select-input"
            />
          )}

          {isCustomMetric && (
            <MetricSelector
              alarm={command.targetTrackingConfiguration.customizedMetricSpecification as IScalingPolicyAlarmView}
              serverGroup={serverGroup}
              updateAlarm={updateAlarm}
            />
          )}
          {allowDualMode && (
            <a className="clickable" onClick={onMetricTypeChange}>
              {isCustomMetric ? 'Use a predefined metric' : 'Select a custom metric'}
            </a>
          )}
        </div>
      </div>
      {!isCustomMetric &&
        command.targetTrackingConfiguration.predefinedMetricSpecification?.predefinedMetricType ===
          'ALBRequestCountPerTarget' && (
          <div className="row sp-margin-s-yaxis">
            <div className="col-md-2 sm-label-right">Target Group</div>
            <div className="col-md-10 content-fields horizontal">
              <ReactSelectInput
                value={command.targetTrackingConfiguration.predefinedMetricSpecification?.resourceLabel}
                options={targetGroupOptions()}
                onChange={(e) =>
                  setCommandField(
                    'targetTrackingConfiguration.predefinedMetricSpecification.resourceLabel',
                    e.target.value,
                  )
                }
                inputClassName="metric-select-input"
              />
            </div>
          </div>
        )}
      <div className="row sp-margin-s-yaxis">
        <div className="col-md-2 sm-label-right">Target</div>
        <div className="col-md-10 content-fields horizontal">
          {isCustomMetric && (
            <div className="horizontal middle">
              <ReactSelectInput
                value={command.targetTrackingConfiguration.customizedMetricSpecification?.statistic}
                stringOptions={statistics}
                onChange={(e) =>
                  setCommandField('targetTrackingConfiguration.customizedMetricSpecification.statistic', e.target.value)
                }
                inputClassName="form-control input-sm target-input"
              />
              <span className="sp-margin-xs-xaxis">of</span>
            </div>
          )}
          <div className="horizontal middle">
            <NumberInput
              value={command.targetTrackingConfiguration.targetValue}
              onChange={(e) =>
                setCommandField('targetTrackingConfiguration.targetValue', Number.parseInt(e.target.value))
              }
              inputClassName="form-control input-sm sp-margin-xs-right"
            />
            <span>{unit}</span>
          </div>
        </div>
      </div>
      <div className="row">
        <div className="col-md-10 col-md-offset-1">
          <TargetTrackingChart
            config={command.targetTrackingConfiguration}
            serverGroup={serverGroup}
            unit={unit}
            updateUnit={(u) => setUnit(u)}
          />
        </div>
      </div>
    </div>
  );
};
