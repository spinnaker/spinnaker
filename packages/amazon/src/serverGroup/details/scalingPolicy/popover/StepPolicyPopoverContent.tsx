import * as React from 'react';

import { LabeledValue, LabeledValueList } from '@spinnaker/core';

import { MetricAlarmChart } from '../chart/MetricAlarmChart';
import type {
  IAmazonServerGroup,
  IScalingPolicyAlarmView,
  IScalingPolicyView,
  IStepAdjustmentView,
} from '../../../../domain';
import { comparatorMap } from '../popover/AlarmSummary';

export interface IStepPolicyPopoverContentProps {
  policy: IScalingPolicyView;
  serverGroup: IAmazonServerGroup;
}

export const StepPolicyPopoverContent = ({ policy, serverGroup }: IStepPolicyPopoverContentProps) => {
  const { adjustmentType, alarms, cooldown, estimatedInstanceWarmup, minAdjustmentMagnitude, stepAdjustments } = policy;
  const showWait = cooldown ? true : stepAdjustments?.[0]?.operator === 'decrease';
  const alarm = alarms[0] || ({} as IScalingPolicyAlarmView);
  const isGreater = alarm?.comparisonOperator.includes('Greater');

  const getAdjustmentTypeString = (step: IScalingPolicyView | IStepAdjustmentView): string => {
    if (adjustmentType === 'ExactCapacity') {
      return `set capacity to ${step.scalingAdjustment} instance${step.scalingAdjustment > 1 ? 's' : ''}`;
    }

    return `${step.operator} capacity by ${step.absAdjustment}${
      adjustmentType === 'PercentChangeInCapacity' ? '%' : ' instance'
    }${adjustmentType === 'ChangeInCapacity' && step.absAdjustment > 1 ? 's' : ''}`;
  };

  const getBoundaryStr = (step: IStepAdjustmentView) => {
    const middleBounds =
      step.metricIntervalLowerBound !== undefined && step.metricIntervalUpperBound !== undefined
        ? `is between ${alarm.threshold + step.metricIntervalLowerBound} and ${
            alarm.threshold + step.metricIntervalUpperBound
          } `
        : '';

    const sourceBound = isGreater ? step.metricIntervalLowerBound : step.metricIntervalUpperBound;
    const finalBound = `is ${isGreater ? 'greater' : 'less'} than ${alarm.threshold + sourceBound}`;

    return `if ${alarm.metricName} ${middleBounds}${finalBound}`;
  };

  return (
    <div>
      <LabeledValueList className="dl-horizontal dl-narrow">
        <LabeledValue
          label="Whenever"
          value={`${alarm.statistic} of ${alarm.metricName} is ${comparatorMap[alarm.comparisonOperator]} ${
            alarm.threshold
          }`}
        />
        <LabeledValue
          label="for at least"
          value={`${alarm.evaluationPeriods} consecutive periods of ${alarm.period} seconds`}
        />
        {Boolean(stepAdjustments?.length) && (
          <LabeledValue
            label="then"
            value={stepAdjustments.map((sa, index) => (
              <div key={`step-adjustment-boundary-${index}`}>
                {stepAdjustments.length > 1 && <span>{getBoundaryStr(sa)}</span>}
                <span>{getAdjustmentTypeString(sa)}</span>
              </div>
            ))}
          />
        )}
        {!Boolean(stepAdjustments?.length) && <LabeledValue label="then" value={getAdjustmentTypeString(policy)} />}
        {Boolean(minAdjustmentMagnitude) && (
          <LabeledValue
            label="in"
            value={`increments of at least ${minAdjustmentMagnitude} instance${minAdjustmentMagnitude > 1 ? 's' : ''}`}
          />
        )}
        {Boolean(showWait) && (
          <LabeledValue label="wait" value={`${cooldown} seconds before allowing another scaling activity.`} />
        )}
        {Boolean(estimatedInstanceWarmup) && (
          <LabeledValue label="wait" value={`${estimatedInstanceWarmup} seconds to warm up after each step.`} />
        )}
      </LabeledValueList>
      <MetricAlarmChart alarm={alarm} serverGroup={serverGroup} />
    </div>
  );
};
