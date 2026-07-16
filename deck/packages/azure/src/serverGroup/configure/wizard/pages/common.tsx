import React from 'react';

import type { IWizardPageComponent } from '@spinnaker/core';

export interface IAzureWizardPageProps {
  formik: any;
  app?: any;
}

export abstract class AzureWizardPage<P extends IAzureWizardPageProps = IAzureWizardPageProps>
  extends React.Component<P>
  implements IWizardPageComponent<any> {
  public validate(_values: any): { [key: string]: any } {
    return {};
  }

  protected setField = (field: string, value: any) => {
    const path = field.split('.');
    const leaf = path.pop();
    const target = path.reduce((acc: any, key) => {
      acc[key] = acc[key] || {};
      return acc[key];
    }, this.props.formik.values);
    target[leaf] = value;
    this.props.formik.setFieldValue(field, value);
  };

  protected textField(label: string, field: string, type = 'text') {
    const value = this.props.formik.values[field] ?? '';
    return (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">{label}</div>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            name={field}
            onChange={(event) => this.setField(field, event.target.value)}
            type={type}
            value={value}
          />
        </div>
      </div>
    );
  }

  protected selectField(label: string, field: string, options: any[], labeler: (option: any) => string = String) {
    const value = this.props.formik.values[field] ?? '';
    return (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">{label}</div>
        <div className="col-md-7">
          <select
            className="form-control input-sm"
            name={field}
            onChange={(event) => this.setField(field, event.target.value)}
            value={value}
          >
            <option value="">Select...</option>
            {(options || []).map((option) => {
              const optionValue = typeof option === 'string' ? option : option.name;
              return (
                <option key={optionValue} value={optionValue}>
                  {labeler(option)}
                </option>
              );
            })}
          </select>
        </div>
      </div>
    );
  }
}
