import React from 'react';

import { Application, ISubnet, HelpField, Markdown } from '@spinnaker/core';
import { AWSProviderSettings } from '../aws.settings';

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
    const isRecommended = (AWSProviderSettings.serverGroups?.recommendedSubnets || []).includes(value);
    const { subnetWarning } = AWSProviderSettings.serverGroups;
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
          {!isRecommended && Boolean(subnetWarning) && (
            <div className="alert alert-warning sp-margin-s-top horizontal center">
              <i className="fa fa-exclamation-triangle sp-margin-s-top" />
              <div className="sp-margin-s-left">
                <Markdown message={subnetWarning} style={{ display: 'inline-block', marginLeft: '2px' }} />
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }
}
