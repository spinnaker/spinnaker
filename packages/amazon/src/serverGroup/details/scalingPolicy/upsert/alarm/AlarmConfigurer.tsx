import * as React from 'react';

import { ICloudMetricStatistics, NumberInput, ReactSelectInput } from '@spinnaker/core';

import { MetricSelector } from './MetricSelector';
import { MetricAlarmChart } from '../../chart/MetricAlarmChart';
import { IAmazonServerGroup, IScalingPolicyAlarm, IStepAdjustment } from '../../../../../domain';

import './AlarmConfigurer.less';

export interface IAlarmConfigurerProps {
  alarm: IScalingPolicyAlarm;
  multipleAlarms: boolean;
  serverGroup: IAmazonServerGroup;
  stepAdjustments: IStepAdjustment[];
  stepsChanged: (steps: IStepAdjustment[]) => void;
  updateAlarm: (alarm: IScalingPolicyAlarm) => void;
}

const STATISTICS = ['Average', 'Maximum', 'Minimum', 'SampleCount', 'Sum'];

const COMPARATORS = [
  { label: '>=', value: 'GreaterThanOrEqualToThreshold' },
  { label: '>', value: 'GreaterThanThreshold' },
  { label: '<=', value: 'LessThanOrEqualToThreshold' },
  { label: '<', value: 'LessThanThreshold' },
];

const PERIODS = [
  { label: '1 minute', value: 60 },
  { label: '5 minutes', value: 60 * 5 },
  { label: '15 minutes', value: 60 * 15 },
  { label: '1 hour', value: 60 * 60 },
  { label: '4 hours', value: 60 * 60 * 4 },
  { label: '1 day', value: 60 * 60 * 24 },
];

export const AlarmConfigurer = ({
  alarm,
  multipleAlarms,
  serverGroup,
  stepAdjustments,
  stepsChanged,
  updateAlarm,
}: IAlarmConfigurerProps) => {
  const comparatorBound = alarm.comparisonOperator?.indexOf('Greater') === 0 ? 'max' : 'min';
  const [alarmView, setAlarmView] = React.useState<IScalingPolicyAlarm>(alarm);
  const [unit, setUnit] = React.useState<string>(alarmView?.unit);

  React.useEffect(() => {
    if (stepAdjustments) {
      const source = comparatorBound === 'max' ? 'metricIntervalLowerBound' : 'metricIntervalUpperBound';
      const newStep: IStepAdjustment = {
        scalingAdjustment: 1,
        [source]: alarm.threshold,
      };
      stepsChanged([newStep]);
    }
  }, [comparatorBound]);

  const onChartLoaded = (stats: ICloudMetricStatistics) => setUnit(stats.unit);

  const onAlarmChange = (key: string, value: any) => {
    const newAlarm = {
      ...alarmView,
      [key]: value,
    };
    setAlarmView(newAlarm);
    updateAlarm(newAlarm);
  };

  const onThresholdChange = (bound: number) => {
    const newAlarm = {
      ...alarmView,
      threshold: bound,
    };
    setAlarmView(newAlarm);
    updateAlarm(newAlarm);

    const source = comparatorBound === 'max' ? 'metricIntervalLowerBound' : 'metricIntervalUpperBound';
    if (stepAdjustments?.length) {
      const updatedStepAdjustments = [...stepAdjustments];
      // Always set the first step at the alarm threshold
      updatedStepAdjustments[0][source] = bound;
      stepsChanged(updatedStepAdjustments);
    }
  };

  const onMetricChange = (newAlarm: IScalingPolicyAlarm) => {
    setAlarmView(newAlarm);
    updateAlarm(newAlarm);
  };

  return (
    <div className="AlarmConfigurer">
      {multipleAlarms && (
        <div className="row">
          <div className="col-md-12">
            <div className="alert alert-warning">
              <p>
                <i className="fa fa-exclamation-triangle"></i> This scaling policy is configured with multiple alarms.
                You are only editing the first alarm.
              </p>
              <p>To edit or remove the additional alarms, you will need to use the AWS console.</p>
            </div>
          </div>
        </div>
      )}
      {alarmView.alarmActionArns?.length > 1 && (
        <div className="row">
          <div className="col-md-12">
            <div className="alert alert-warning">
              <p>
                <i className="fa fa-exclamation-triangle"></i> This alarm is used in multiple scaling policies. Any
                changes here will affect those other scaling policies.
              </p>
            </div>
          </div>
        </div>
      )}
      <div className="row sp-margin-s-yaxis">
        <div className="col-md-2 sm-label-right">Whenever</div>
        <div className="col-md-10 horizontal middle">
          <ReactSelectInput
            value={alarmView.statistic}
            onChange={(e) => onAlarmChange('statistic', e.target.value)}
            stringOptions={STATISTICS}
            clearable={false}
            inputClassName="sp-margin-xs-right configurer-field-lg"
          />
          <span className="input-label sp-margin-xs-right"> of </span>
          <MetricSelector alarm={alarmView} serverGroup={serverGroup} updateAlarm={onMetricChange} />
        </div>
      </div>
      <div className="row sp-margin-s-yaxis">
        <div className="col-md-2 sm-label-right">is</div>
        <div className="col-md-10 horizontal middle">
          <ReactSelectInput
            value={alarmView.comparisonOperator}
            onChange={(e) => onAlarmChange('comparisonOperator', e.target.value)}
            options={COMPARATORS}
            clearable={false}
            inputClassName="sp-margin-s-right configurer-field-small"
          />
          <div className="sp-margin-xl-left">
            <NumberInput
              value={alarmView.threshold}
              onChange={(e) => onThresholdChange(Number.parseInt(e.target.value))}
              inputClassName="sp-margin-xs-right configurer-field-lg"
            />
          </div>
          <span className="input-label">{unit}</span>
        </div>
      </div>
      <div className="row sp-margin-s-yaxis">
        <div className="col-md-2 sm-label-right">for at least</div>
        <div className="col-md-10 horizontal middle">
          <NumberInput
            value={alarmView.evaluationPeriods}
            onChange={(e) => onAlarmChange('evaluationPeriods', Number.parseInt(e.target.value))}
            inputClassName="configurer-field-med number-input-field"
          />
          <span className="input-label sp-margin-s-xaxis"> consecutive period(s) of </span>
          <ReactSelectInput
            value={alarmView.period}
            onChange={(e) => onAlarmChange('period', e.target.value)}
            options={PERIODS}
            clearable={false}
            inputClassName="sp-margin-xs-right configurer-field-lg"
          />
        </div>
      </div>
      <div className="row sp-margin-s-yaxis" ng-if="$ctrl.alarm.metricName">
        <div className="col-md-10 col-md-offset-1">
          {alarm && (
            <div>
              <MetricAlarmChart alarm={alarmView} serverGroup={serverGroup} onChartLoaded={onChartLoaded} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
