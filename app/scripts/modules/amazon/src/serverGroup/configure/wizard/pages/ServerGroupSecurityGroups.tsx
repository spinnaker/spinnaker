import * as React from 'react';
import { FormikErrors } from 'formik';

import { IWizardPageProps, wizardPage, FirewallLabels } from '@spinnaker/core';

import { SecurityGroupSelector } from '../securityGroups/SecurityGroupSelector';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';
import { ServerGroupSecurityGroupsRemoved } from '../securityGroups/ServerGroupSecurityGroupsRemoved';

export type IServerGroupSecurityGroupsProps = IWizardPageProps<IAmazonServerGroupCommand>;

class ServerGroupSecurityGroupsImpl extends React.Component<IServerGroupSecurityGroupsProps> {
  public static get LABEL() {
    return FirewallLabels.get('Firewalls');
  }

  public validate(values: IAmazonServerGroupCommand) {
    const errors: FormikErrors<IAmazonServerGroupCommand> = {};

    if (values.viewState.dirty.securityGroups) {
      errors.securityGroups = 'You must acknowledge removed security groups.';
    }

    return errors;
  }

  private onChange = (securityGroups: string[]) => {
    this.props.formik.setFieldValue('securityGroups', securityGroups);
  };

  private acknowledgeRemovedGroups = () => {
    this.props.formik.values.viewState.dirty.securityGroups = null;
    this.setState({});
  };

  public render() {
    const { values } = this.props.formik;

    return (
      <div className="row">
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

export const ServerGroupSecurityGroups = wizardPage(ServerGroupSecurityGroupsImpl);
