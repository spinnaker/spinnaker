import { FormikProps } from 'formik';
import React from 'react';

import { IWizardPageComponent } from '@spinnaker/core';

import { SecurityGroupSelector } from '../securityGroups/SecurityGroupSelector';
import { ServerGroupSecurityGroupsRemoved } from '../securityGroups/ServerGroupSecurityGroupsRemoved';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupSecurityGroupsProps {
  formik: FormikProps<IAmazonServerGroupCommand>;
}

export class ServerGroupSecurityGroups
  extends React.Component<IServerGroupSecurityGroupsProps>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  public validate(values: IAmazonServerGroupCommand) {
    const errors = {} as any;

    if (values.viewState.dirty.securityGroups) {
      errors.securityGroups = 'You must acknowledge removed security groups.';
    }

    return errors;
  }

  private onChange = (securityGroups: string[]) => {
    this.props.formik.setFieldValue('securityGroups', securityGroups);
  };

  private acknowledgeRemovedGroups = () => {
    const { viewState } = this.props.formik.values;
    viewState.dirty.securityGroups = null;
    this.props.formik.setFieldValue('viewState', viewState);
  };

  public render() {
    const { values } = this.props.formik;

    return (
      <div className="container-fluid form-horizontal">
        <ServerGroupSecurityGroupsRemoved command={values} onClear={this.acknowledgeRemovedGroups} />
        <SecurityGroupSelector
          command={values}
          availableGroups={values.backingData.filtered.securityGroups}
          groupsToEdit={values.securityGroups}
          onChange={this.onChange}
        />
      </div>
    );
  }
}
