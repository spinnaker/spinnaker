import React from 'react';

import { AzureWizardPage } from './common';

export class ServerGroupSecurityGroups extends AzureWizardPage {
  private securityGroupChanged = (securityGroupId: string) => {
    const securityGroups = this.props.formik.values.backingData?.filtered?.securityGroups || [];
    const selectedSecurityGroup = securityGroups.find(
      (securityGroup: any) => (securityGroup.id || securityGroup.name) === securityGroupId,
    );
    this.props.formik.values.selectedSecurityGroup = selectedSecurityGroup || null;
    this.props.formik.values.securityGroupName = securityGroupId || null;
    this.props.formik.setFieldValue('selectedSecurityGroup', selectedSecurityGroup || null);
    this.props.formik.setFieldValue('securityGroupName', securityGroupId || null);
  };

  public render() {
    const { values } = this.props.formik;
    const securityGroups = values.backingData?.filtered?.securityGroups || [];
    if (!values.viewState?.securityGroupsConfigured) {
      return <h5 className="text-center">Please select an account and region.</h5>;
    }

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Firewall</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              onChange={(event) => this.securityGroupChanged(event.target.value)}
              value={values.selectedSecurityGroup?.id || values.securityGroupName || ''}
            >
              <option value="">Select...</option>
              {securityGroups.map((securityGroup: any) => {
                const securityGroupId = securityGroup.id || securityGroup.name || securityGroup;
                return (
                  <option key={securityGroupId} value={securityGroupId}>
                    {securityGroupId}
                  </option>
                );
              })}
            </select>
          </div>
        </div>
        <div className="form-group small" style={{ marginTop: '20px' }}>
          <div className="col-md-9 col-md-offset-3">
            <p>If you're not finding a firewall that was recently added, refresh the list and reopen this dialog.</p>
          </div>
        </div>
      </div>
    );
  }
}
