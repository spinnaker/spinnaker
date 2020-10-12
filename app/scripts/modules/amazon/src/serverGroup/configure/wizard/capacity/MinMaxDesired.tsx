import React from 'react';
import { IServerGroupCommand, SpelNumberInput } from '@spinnaker/core';

export interface IMinMaxDesiredProps {
  command: IServerGroupCommand;
  fieldChanged: (fieldName: string, value: number | string) => void;
}

export interface IMinMaxDesiredState {}

export class MinMaxDesired extends React.Component<IMinMaxDesiredProps, IMinMaxDesiredState> {
  public render() {
    const {
      command: {
        capacity: { min, max, desired },
      },
      fieldChanged,
    } = this.props;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-2">Min</div>
          <div className="col-md-8">
            <SpelNumberInput
              value={min}
              min={0}
              max={typeof max === 'number' ? max : undefined}
              onChange={(v) => fieldChanged('min', v)}
              required={true}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-2">Max</div>
          <div className="col-md-8">
            <SpelNumberInput
              value={max}
              min={typeof min === 'number' ? min : undefined}
              onChange={(v) => fieldChanged('max', v)}
              required={true}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-2">Desired</div>
          <div className="col-md-8">
            <SpelNumberInput
              value={desired}
              min={typeof min === 'number' ? min : undefined}
              max={typeof max === 'number' ? max : undefined}
              onChange={(v) => fieldChanged('desired', v)}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
