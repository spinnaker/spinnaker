import * as React from 'react';

import { NumberInput, ReactSelectInput } from '@spinnaker/core';

import type { IScalingPolicyAlarm, IStepAdjustment } from '../../../../../domain';

import './StepPolicyAction.less';

export type Operator = 'Add' | 'Remove' | 'Set to';
export type AdjustmentTypeView = 'instances' | 'percent of group';

export interface IStepPolicyActionProps {
  adjustmentType: AdjustmentTypeView;
  adjustmentTypeChanged: (action: Operator, type: AdjustmentTypeView) => void;
  alarm: IScalingPolicyAlarm;
  isMin: boolean;
  operator: Operator;
  step: { stepAdjustments: IStepAdjustment[] };
  stepAdjustments?: IStepAdjustment[];
  stepsChanged: (steps: IStepAdjustment[]) => void;
}

export const StepPolicyAction = ({
  adjustmentType,
  adjustmentTypeChanged,
  alarm,
  isMin,
  operator,
  step,
  stepAdjustments,
  stepsChanged,
}: IStepPolicyActionProps) => {
  const hasEqualTo = alarm?.comparisonOperator.includes('Equal');
  const availableActions = ['Add', 'Remove', 'Set to'];

  const adjustmentTypeOptions = operator === 'Set to' ? ['instances'] : ['instances', 'percent of group'];
  const onActionChange = (val: Operator) => {
    adjustmentTypeChanged(val, adjustmentType);
  };

  const onAdjustmentTypeChange = (type: AdjustmentTypeView) => {
    adjustmentTypeChanged(operator, type);
  };

  const steps = step?.stepAdjustments || stepAdjustments;
  const addStep = () => {
    const newStep = { scalingAdjustment: 1 } as IStepAdjustment;
    const newSteps = [...steps, newStep];
    stepsChanged(newSteps);
  };
  const removeStep = (index: number) => {
    const newSteps = steps.filter((_s, i) => i !== index);
    stepsChanged(newSteps);
  };
  const updateStep = (updatedStep: IStepAdjustment, index: number) => {
    const newSteps = [...steps];
    newSteps[index] = updatedStep;
    stepsChanged(newSteps);
  };

  return (
    <div className="StepPolicyAction row">
      {steps?.map((step: IStepAdjustment, index: number) => (
        <div key={`step-adjustment-${index}`} className="step-policy-row col-md-10 col-md-offset-1 horizontal middle">
          {Boolean(index) ? (
            <span className="action-input sp-margin-xs-left">{operator}</span>
          ) : (
            <ReactSelectInput
              value={operator}
              stringOptions={availableActions}
              onChange={(e) => onActionChange(e.target.value)}
              clearable={false}
              inputClassName="action-input sp-margin-xs-right"
            />
          )}
          <NumberInput
            value={step.scalingAdjustment}
            min={1}
            onChange={(e) => updateStep({ ...step, scalingAdjustment: Number.parseInt(e.target.value) }, index)}
            inputClassName="action-input"
          />
          {Boolean(index) ? (
            <span className="sp-margin-xs-left">{adjustmentType}</span>
          ) : (
            <ReactSelectInput
              value={adjustmentType}
              stringOptions={adjustmentTypeOptions}
              onChange={(e) => onAdjustmentTypeChange(e.target.value)}
              clearable={false}
              inputClassName="adjustment-type-input sp-margin-xs-left"
            />
          )}
          <span className="sp-margin-xs-xaxis">
            {' '}
            when <b>{alarm?.metricName}</b> is{' '}
          </span>
          {index === steps.length - 1 && (
            <span>{` ${isMin ? 'less' : 'greater'} than${!Boolean(index) || hasEqualTo ? ' or equal to' : ''} ${
              isMin ? step.metricIntervalUpperBound || '' : step.metricIntervalLowerBound || ''
            } `}</span>
          )}
          {index < steps.length - 1 && (
            <>
              <span className="sp-margin-xs-xaxis">between</span>
              {isMin ? (
                <NumberInput
                  value={step.metricIntervalLowerBound}
                  max={step.metricIntervalUpperBound}
                  step={0.1}
                  onChange={(e) =>
                    updateStep({ ...step, metricIntervalLowerBound: Number.parseFloat(e.target.value) }, index)
                  }
                  inputClassName="action-input"
                />
              ) : (
                <span>{step.metricIntervalLowerBound}</span>
              )}
              <span className="sp-margin-xs-xaxis">and</span>
              {isMin ? (
                <span>{step.metricIntervalUpperBound}</span>
              ) : (
                <NumberInput
                  value={step.metricIntervalUpperBound}
                  min={step.metricIntervalLowerBound}
                  step={0.1}
                  onChange={(e) =>
                    updateStep({ ...step, metricIntervalUpperBound: Number.parseFloat(e.target.value) }, index)
                  }
                  inputClassName="action-input"
                />
              )}
            </>
          )}
          {Boolean(index) && (
            <a
              className="glyphicon glyphicon-trash clickable sp-margin-xs-xaxis remove-step-action-icon"
              onClick={() => removeStep(index)}
            />
          )}
        </div>
      ))}
      <div className="row sp-margin-s">
        <div className="col-md-10 col-md-offset-1">
          <button type="button" className="btn btn-block btn-sm add-new" onClick={addStep}>
            <span className="glyphicon glyphicon-plus-sign"></span>
            Add step
          </button>
        </div>
      </div>
      <div className="row sp-margin-s-xaxis">
        <div className="col-md-10 col-md-offset-1">
          <a
            href="http://docs.aws.amazon.com/autoscaling/latest/userguide/as-scale-based-on-demand.html#as-scaling-steps"
            target="_blank"
          >
            <i className="far fa-file-alt sp-margin-xs-right" />
            Documentation
          </a>
        </div>
      </div>
    </div>
  );
};
