import React from 'react';
import Select, { Option } from 'react-select';

import { HelpField, IServerGroupCommand, SpelNumberInput } from '@spinnaker/core';
import { IMinMaxDesiredProps } from './MinMaxDesired';

export interface ICapacitySelectorProps {
  command: IServerGroupCommand;
  setFieldValue: (field: keyof IServerGroupCommand, value: any, shouldValidate?: boolean) => void;
  MinMaxDesired: React.ComponentClass<IMinMaxDesiredProps>;
}

export class CapacitySelector extends React.Component<ICapacitySelectorProps> {
  private preferSourceCapacityOptions = [
    { label: 'fail the stage', value: false },
    { label: 'use fallback values', value: true },
  ];

  private useSourceCapacityUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const value = event.target.value === 'true';
    const { command } = this.props;
    this.props.setFieldValue('useSourceCapacity', value);

    if (!value) {
      delete command.preferSourceCapacity;
      this.props.setFieldValue('preferSourceCapacity', undefined);
    }
    this.setState({});
  };

  private setSimpleCapacity(simpleCapacity: boolean) {
    const { command } = this.props;
    const newViewState = {
      ...command.viewState,
      useSimpleCapacity: simpleCapacity,
    };
    this.props.setFieldValue('useSourceCapacity', false);
    this.props.setFieldValue('viewState', newViewState);
    this.setMinMax(command.capacity.desired);
    this.setState({});
  }

  private simpleInstancesChanged = (value: number | string) => {
    this.setMinMax(value);
  };

  private setMinMax(value: number | string) {
    const { command } = this.props;
    if (command.viewState.useSimpleCapacity) {
      command.capacity = { min: value, max: value, desired: value };
      this.props.setFieldValue('useSourceCapacity', false);
      this.props.setFieldValue('capacity', command.capacity);
    }
    this.setState({});
  }

  private preferSourceCapacityChanged = (option: Option<boolean>) => {
    this.props.setFieldValue('preferSourceCapacity', option && option.value ? true : undefined);
    this.setState({});
  };

  private capacityFieldChanged = (fieldName: 'min' | 'max' | 'desired', value: number | string) => {
    const { command, setFieldValue } = this.props;
    command.capacity = { ...command.capacity };
    command.capacity[fieldName] = value;
    setFieldValue('capacity', command.capacity);
  };

  public render() {
    const { command, MinMaxDesired } = this.props;

    const readOnlyFields = command.viewState.readOnlyFields || {};

    if (!command.viewState.useSimpleCapacity || command.useSourceCapacity) {
      return (
        <div>
          <div className="form-group">
            <div className="col-md-12">
              <p>Sets up auto-scaling constraints for this server group.</p>
              <p>
                To set min, max, and desired instance counts to the same value use the{' '}
                <a className="clickable" onClick={() => this.setSimpleCapacity(true)}>
                  Simple Mode
                </a>
                .
              </p>
            </div>
          </div>

          {/* // TODO: Test this in a clone server group dialog or an edit pipeline dialog */}
          {!readOnlyFields.useSourceCapacity && command.viewState.mode === 'editPipeline' && (
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Capacity</div>
              <div className="col-md-9 radio">
                <label>
                  <input
                    type="radio"
                    checked={command.useSourceCapacity}
                    value="true"
                    id="useSourceCapacityTrue"
                    onChange={this.useSourceCapacityUpdated}
                  />
                  Copy the capacity from the current server group
                  <HelpField id="serverGroupCapacity.useSourceCapacityTrue" />
                </label>
              </div>
              {command.useSourceCapacity && (
                <div className="col-md-9 col-md-offset-3 radio" style={{ paddingLeft: '35px' }}>
                  <div>
                    If no current server group is found,
                    <Select
                      clearable={false}
                      value={!!command.preferSourceCapacity}
                      options={this.preferSourceCapacityOptions}
                      onChange={this.preferSourceCapacityChanged}
                    />
                  </div>
                  {command.preferSourceCapacity && (
                    <div>
                      <b>Fallback values</b>
                      <MinMaxDesired command={command} fieldChanged={this.capacityFieldChanged} />
                    </div>
                  )}
                </div>
              )}

              <div className="col-md-9 col-md-offset-3 radio">
                <label>
                  <input
                    type="radio"
                    checked={!command.useSourceCapacity}
                    value="false"
                    id="useSourceCapacityFalse"
                    onChange={this.useSourceCapacityUpdated}
                  />
                  Let me specify the capacity
                  <HelpField id="serverGroupCapacity.useSourceCapacityFalse" />
                </label>
              </div>
            </div>
          )}

          {(!command.useSourceCapacity || command.viewState.mode !== 'editPipeline') && (
            <div>
              <div className="col-md-9 col-md-offset-3">
                <MinMaxDesired command={command} fieldChanged={this.capacityFieldChanged} />
              </div>
            </div>
          )}
        </div>
      );
    }

    return (
      <div>
        <div className="form-group">
          <div className="col-md-12">
            <p>Sets the min, max, and desired instance counts to the same value.</p>

            <p>
              {' '}
              To set capacity for auto-scaling, use the{' '}
              <a className="clickable" onClick={() => this.setSimpleCapacity(false)}>
                Advanced Mode
              </a>
              .
            </p>
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Number of Instances</div>
          <div className="col-md-8">
            <SpelNumberInput value={command.capacity.desired} min={0} onChange={this.simpleInstancesChanged} />
          </div>
        </div>
      </div>
    );
  }
}
