import React from 'react';

import { NameUtils } from '@spinnaker/core';

import { AzureWizardPage } from './common';

export class ServerGroupBasicSettings extends AzureWizardPage {
  public validate(values: any): { [key: string]: any } {
    const errors: { [key: string]: any } = {};
    const stackPattern = values.viewState?.templatingEnabled ? /^([a-zA-Z0-9]*(\${.+})*)*$/ : /^[a-zA-Z0-9]*$/;
    const detailPattern = values.viewState?.templatingEnabled ? /^([a-zA-Z0-9-]*(\${.+})*)*$/ : /^[a-zA-Z0-9-]*$/;

    if (!values.credentials) {
      errors.credentials = 'Account required.';
    }
    if (!values.region) {
      errors.region = 'Region required.';
    }
    if (!values.stack) {
      errors.stack = 'Stack required.';
    }
    if (!values.freeFormDetails) {
      errors.freeFormDetails = 'Detail required.';
    }
    if (!stackPattern.test(values.stack || '')) {
      errors.stack = 'Stack can only contain letters and numbers.';
    }
    if (!detailPattern.test(values.freeFormDetails || '')) {
      errors.freeFormDetails = 'Detail can only contain letters, numbers, and dashes.';
    }
    return errors;
  }

  private accountChanged = (account: string) => {
    const { values } = this.props.formik;
    values.credentials = account;
    const result = values.credentialsChanged?.(values);
    this.props.formik.setFieldValue('credentials', account);
    this.props.formik.setFieldValue('region', values.region);
    values.processCommandUpdateResult?.(result);
  };

  private regionChanged = (region: string) => {
    const { values } = this.props.formik;
    values.region = region;
    const result = values.regionChanged?.(values);
    this.props.formik.setFieldValue('region', region);
    values.processCommandUpdateResult?.(result);
  };

  public render() {
    const { app, formik } = this.props;
    const { values } = formik;
    const clusterName = NameUtils.getClusterName(app?.name || values.application, values.stack, values.freeFormDetails);
    const accounts = values.backingData?.accounts || [];
    const regions = values.backingData?.filtered?.regions || [];

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              onChange={(event) => this.accountChanged(event.target.value)}
              value={values.credentials || ''}
            >
              <option value="">Select...</option>
              {accounts.map((account: string) => (
                <option key={account} value={account}>
                  {account}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Region</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              onChange={(event) => this.regionChanged(event.target.value)}
              value={values.region || ''}
            >
              <option value="">Select...</option>
              {regions.map((region: any) => {
                const name = typeof region === 'string' ? region : region.name;
                return (
                  <option key={name} value={name}>
                    {name}
                  </option>
                );
              })}
            </select>
          </div>
        </div>
        {this.textField('Stack', 'stack')}
        {this.textField('Detail', 'freeFormDetails')}
        {!values.viewState?.hideClusterNamePreview && (
          <div className="well text-center">
            Your server group will be in the cluster: <strong>{clusterName}</strong>
          </div>
        )}
      </div>
    );
  }
}
