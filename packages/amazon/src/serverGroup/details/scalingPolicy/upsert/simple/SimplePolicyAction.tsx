import * as React from 'react';

import { NumberInput, ReactSelectInput } from '@spinnaker/core';

import './SimplePolicyAction.less';

type Operator = 'Add' | 'Remove' | 'Set to';
type AdjustmentTypeView = 'instances' | 'percent of group';

export interface ISimplePolicyActionProps {
  adjustmentType: AdjustmentTypeView;
  adjustmentTypeChanged: (action: Operator, type: AdjustmentTypeView) => void;
  operator: Operator;
  scalingAdjustment: number;
  updateScalingAdjustment: (adjustment: number) => void;
}

export const SimplePolicyAction = ({
  adjustmentType,
  adjustmentTypeChanged,
  operator,
  scalingAdjustment,
  updateScalingAdjustment,
}: ISimplePolicyActionProps) => {
  const availableActions = ['Add', 'Remove', 'Set to'];

  const adjustmentTypeOptions = operator === 'Set to' ? ['instances'] : ['instances', 'percent of group'];
  const onActionChange = (val: Operator) => {
    adjustmentTypeChanged(val, adjustmentType);
  };

  const [adjustmentTypeView, setAdjustmentTypeView] = React.useState<AdjustmentTypeView>(adjustmentType);
  const onAdjustmentTypeChange = (type: AdjustmentTypeView) => {
    setAdjustmentTypeView(type);
    adjustmentTypeChanged(operator, type);
  };

  const [adjustment, setAdjustment] = React.useState<number>(scalingAdjustment);
  const onScalingAdjustmentChange = (adj: number) => {
    setAdjustment(adj);
    updateScalingAdjustment(adj);
  };

  return (
    <div className="SimplePolicyAction row">
      <div className="col-md-10 col-md-offset-1 horizontal middle">
        <ReactSelectInput
          value={operator}
          stringOptions={availableActions}
          onChange={(e) => onActionChange(e.target.value)}
          clearable={false}
          inputClassName="action-input"
        />
        <NumberInput
          value={adjustment}
          min={1}
          onChange={(e) => onScalingAdjustmentChange(Number.parseInt(e.target.value))}
          inputClassName="action-input"
        />
        <ReactSelectInput
          value={adjustmentTypeView}
          stringOptions={adjustmentTypeOptions}
          onChange={(e) => onAdjustmentTypeChange(e.target.value)}
          clearable={false}
          inputClassName="adjustment-type-input"
        />
      </div>
    </div>
  );
};
