import * as React from 'react';

import { Application, ISubnet, HelpField } from '@spinnaker/core';

import { SubnetSelectInput } from './SubnetSelectInput';

export interface ISubnetSelectFieldProps {
  application: Application;
  component: { [key: string]: any };
  field: string;
  helpKey: string;
  hideClassic?: boolean;
  labelColumns: number;
  onChange: () => void;
  readOnly?: boolean;
  region: string;
  subnets: ISubnet[];
}

export class SubnetSelectField extends React.Component<ISubnetSelectFieldProps> {
  private handleChange = (event: React.ChangeEvent<any>) => {
    const { component, onChange, field } = this.props;
    component[field] = event.target.value;
    onChange();
  };

  public render() {
    const { labelColumns, helpKey, component, region, field, ...otherProps } = this.props;
    const value = component[field];

    return (
      <div className="form-group">
        <div className={`col-md-${labelColumns} sm-label-right`}>
          VPC Subnet <HelpField id={helpKey} />
        </div>

        <div className="col-md-7">
          {region ? (
            <SubnetSelectInput
              {...otherProps}
              inputClassName="form-control input-sm"
              credentials={component.credentials}
              region={region}
              value={value}
              onChange={this.handleChange}
            />
          ) : (
            '(Select an account)'
          )}
        </div>
      </div>
    );
  }
}
