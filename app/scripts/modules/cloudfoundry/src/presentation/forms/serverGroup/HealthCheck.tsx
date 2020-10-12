import React from 'react';

import { FormikFormField, RadioButtonInput, TextInput } from '@spinnaker/core';

export interface IHealthCheckProps {
  healthCheckHttpEndpointFieldName: string;
  healthCheckType: string;
  healthCheckTypeFieldName: string;
  onHealthCheckHttpEndpointChange?: (value: string) => void;
  onHealthCheckTypeChange?: (value: string) => void;
}

export class HealthCheck extends React.Component<IHealthCheckProps> {
  private HEALTH_CHECK_TYPE_OPTIONS = [
    { label: 'port', value: 'port' },
    { label: 'HTTP', value: 'http' },
    { label: 'process', value: 'process' },
  ];

  public render() {
    const {
      healthCheckTypeFieldName,
      healthCheckType,
      healthCheckHttpEndpointFieldName,
      onHealthCheckTypeChange,
      onHealthCheckHttpEndpointChange,
    } = this.props;

    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name={healthCheckTypeFieldName}
            label="Health Check Type"
            input={(props) => <RadioButtonInput {...props} inline={true} options={this.HEALTH_CHECK_TYPE_OPTIONS} />}
            onChange={(value) => {
              onHealthCheckTypeChange && onHealthCheckTypeChange(value);
            }}
          />
        </div>
        {healthCheckType === 'http' && (
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name={healthCheckHttpEndpointFieldName}
              label="Endpoint"
              input={(props) => <TextInput {...props} required={true} />}
              onChange={(value) => {
                onHealthCheckHttpEndpointChange && onHealthCheckHttpEndpointChange(value);
              }}
              required={true}
            />
          </div>
        )}
      </div>
    );
  }
}
