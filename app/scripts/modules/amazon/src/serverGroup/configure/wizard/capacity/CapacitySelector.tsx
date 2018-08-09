import * as React from 'react';
import Select, { Option } from 'react-select';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';
import { HelpField } from '@spinnaker/core';
import { IMinMaxDesiredProps } from './AmazonMinMaxDesired';

export interface ICapacitySelectorProps {
  command: IAmazonServerGroupCommand;
  setFieldValue: (field: keyof IAmazonServerGroupCommand, value: any, shouldValidate?: boolean) => void;
  MinMaxDesired: React.ComponentClass<IMinMaxDesiredProps>;
}

export class CapacitySelector extends React.Component<ICapacitySelectorProps> {
  public preferSourceCapacityOptions = [
    { label: 'fail the stage', value: undefined },
    { label: 'use fallback values', value: true },
  ];

  public useSourceCapacityUpdated(event: React.ChangeEvent<HTMLInputElement>): void {
    const value = event.target.value === 'true';
    const { command } = this.props;
    this.props.setFieldValue('useSourceCapacity', value);

    if (!value) {
      delete command.preferSourceCapacity;
      this.props.setFieldValue('preferSourceCapacity', undefined);
    }
    this.setState({});
  }

  public setSimpleCapacity(simpleCapacity: boolean) {
    const { command } = this.props;
    command.viewState.useSimpleCapacity = simpleCapacity;
    this.props.setFieldValue('useSourceCapacity', false);
    this.setMinMax(command.capacity.desired);
    this.setState({});
  }

  public simpleInstancesChanged = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = Number.parseInt(event.target.value, 10);
    this.setMinMax(value);
  };

  public setMinMax(value: number) {
    const { command } = this.props;
    if (command.viewState.useSimpleCapacity) {
      command.capacity.min = value;
      command.capacity.max = value;
      command.capacity.desired = value;
      this.props.setFieldValue('useSourceCapacity', false);
      this.props.setFieldValue('capacity', command.capacity);
    }
    this.setState({});
  }

  public preferSourceCapacityChanged(option: Option<boolean>) {
    this.props.setFieldValue('preferSourceCapacity', option.value);
    this.setState({});
  }

  private capacityFieldChanged = (fieldName: 'min' | 'max' | 'desired', value: string) => {
    const { command, setFieldValue } = this.props;
    const num = Number.parseInt(value, 10);
    command.capacity[fieldName] = num;
    setFieldValue('capacity', command.capacity);
  };

  public render() {
    const { command, MinMaxDesired } = this.props;

    const readOnlyFields = command.viewState.readOnlyFields || {};

    if (!command.viewState.useSimpleCapacity) {
      return (
        <div>
          <div className="form-group">
            <div className="col-md-12">
              <p>Sets up auto-scaling for this server group.</p>
              <p>
                To disable auto-scaling, use the{' '}
                <a className="clickable" onClick={() => this.setSimpleCapacity(true)}>
                  Simple Mode
                </a>.
              </p>
            </div>
          </div>

          {/* // TODO: Test this in a clone server group dialog or an edit pipeline dialog */}
          {!readOnlyFields.useSourceCapacity &&
            command.viewState.mode === 'editPipeline' && (
              <div className="form-group">
                <div className="col-md-3 sm-label-right">Capacity</div>
                <div className="col-md-9 radio">
                  <label>
                    <input
                      type="radio"
                      checked={command.useSourceCapacity}
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
                        value={command.preferSourceCapacity}
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
                      checked={command.useSourceCapacity}
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
            <p>Sets min, max, and desired instance counts to the same value.</p>

            <p>
              {' '}
              To allow true auto-scaling, use the{' '}
              <a className="clickable" onClick={() => this.setSimpleCapacity(false)}>
                Advanced Mode
              </a>.
            </p>
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Number of Instances</div>
          <div className="col-md-2">
            <input
              type="number"
              onChange={this.simpleInstancesChanged}
              className="form-control input-sm"
              value={command.capacity.desired}
              min={0}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
