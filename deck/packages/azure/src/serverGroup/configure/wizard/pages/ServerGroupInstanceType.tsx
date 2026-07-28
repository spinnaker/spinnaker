import React from 'react';

import { AzureWizardPage } from './common';

export class ServerGroupInstanceType extends AzureWizardPage {
  public validate(values: any): { [key: string]: any } {
    return values.instanceType ? {} : { instanceType: 'Instance Type required.' };
  }

  private instanceTypeChanged = (instanceType: string) => {
    this.props.formik.values.instanceType = instanceType;
    this.props.formik.setFieldValue('instanceType', instanceType);
    this.props.formik.setFieldValue('sku.name', instanceType);
  };

  public render() {
    const { values } = this.props.formik;
    const instanceTypes = values.backingData?.filtered?.instanceTypes || [];
    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Instance Type</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              onChange={(event) => this.instanceTypeChanged(event.target.value)}
              value={values.instanceType || ''}
            >
              <option value="">Select...</option>
              {instanceTypes.map((type: string) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>
    );
  }
}
