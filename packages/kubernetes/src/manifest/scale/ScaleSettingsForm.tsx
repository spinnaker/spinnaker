import React, { useState } from 'react';
import { NumberInput, StageConfigField } from '@spinnaker/core';
import type { IScaleCommand } from './scale.controller';

export interface IScaleSettingsFormProps {
  options: IScaleCommand;
  onChange(options: IScaleCommand): void;
}

export interface IScaleSettingsFormState {
  options: IScaleCommand;
}

export function ScaleSettingsForm({ options, onChange }: IScaleSettingsFormProps) {
  const [state, setState] = useState<IScaleSettingsFormState>({
    options: options,
  });

  const updateReplicas = (newReplicas: number) => {
    state.options.replicas = newReplicas;
    if (onChange) {
      onChange(state.options);
    }
    setState({ options: state.options });
  };

  return (
    <div className="form-horizontal">
      <StageConfigField label="Replicas" fieldColumns={4} groupClassName="form-group form-inline">
        <div className="input-group">
          <NumberInput
            inputClassName="input-sm highlight-pristine"
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
              updateReplicas(e.target.valueAsNumber);
            }}
            value={options.replicas}
            min={0}
          />
        </div>
      </StageConfigField>
    </div>
  );
}
