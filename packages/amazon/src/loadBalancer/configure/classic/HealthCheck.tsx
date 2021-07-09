import { FormikProps } from 'formik';
import React from 'react';

import { FormikFormField, NumberInput, SelectInput, TextInput } from '@spinnaker/core';
import { IAmazonClassicLoadBalancerUpsertCommand } from '../../../domain';

export interface IHealthCheckProps {
  formik: FormikProps<IAmazonClassicLoadBalancerUpsertCommand>;
}

export class HealthCheck extends React.Component<IHealthCheckProps> {
  public requiresHealthCheckPath(): boolean {
    const { values } = this.props.formik;
    return values.healthCheckProtocol && values.healthCheckProtocol.indexOf('HTTP') === 0;
  }

  private healthCheckPathChanged = (value: string) => {
    if (value && value.indexOf('/') !== 0) {
      this.props.formik.setFieldValue('healthCheckPath', `/${value}`);
    }
  };

  public render() {
    return (
      <div className="container-fluid form-horizontal">
        <div className="col-md-4 sm-label-right">Ping</div>
        <div className="col-md-8">
          <table className="table table-condensed packed">
            <thead>
              <tr>
                <th style={{ width: '35%' }}>Protocol</th>
                <th style={{ width: '30%' }}>Port</th>
                <th>{this.requiresHealthCheckPath() && <span>Path</span>}</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <FormikFormField
                    name="healthCheckProtocol"
                    required={true}
                    input={(props) => <SelectInput {...props} options={['HTTP', 'HTTPS', 'SSL', 'TCP']} />}
                  />
                </td>
                <td>
                  <FormikFormField
                    name="healthCheckPort"
                    required={true}
                    input={(props) => <NumberInput {...props} min={1} max={65534} />}
                  />
                </td>
                <td>
                  {this.requiresHealthCheckPath() && (
                    <FormikFormField
                      name="healthCheckPath"
                      input={(props) => <TextInput {...props} />}
                      required={true}
                      onChange={this.healthCheckPathChanged}
                    />
                  )}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    );
  }
}
