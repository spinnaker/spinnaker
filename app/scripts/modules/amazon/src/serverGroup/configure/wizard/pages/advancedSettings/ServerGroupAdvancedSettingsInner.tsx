import * as React from 'react';
import { FormikProps } from 'formik';

import { IWizardPageProps, Overridable } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from 'amazon/serverGroup/configure/serverGroupConfiguration.service';
import { ServerGroupAdvancedSettingsCommon } from './ServerGroupAdvancedSettingsCommon';
import { IServerGroupAdvancedSettingsProps } from './ServerGroupAdvancedSettings';

@Overridable('aws.serverGroup.advancedSettings')
export class ServerGroupAdvancedSettingsInner extends React.Component<
  IServerGroupAdvancedSettingsProps & IWizardPageProps & FormikProps<IAmazonServerGroupCommand>
> {
  private validators = new Map();

  public validate = (values: IAmazonServerGroupCommand): { [key: string]: string } => {
    const errors: { [key: string]: string } = {};

    this.validators.forEach(validator => {
      const subErrors = validator(values);
      Object.assign(errors, { ...subErrors });
    });

    return errors;
  };

  private handleRef = (ele: any) => {
    if (ele) {
      this.validators.set('common', ele.validate);
    } else {
      this.validators.delete('common');
    }
  };

  public render() {
    return <ServerGroupAdvancedSettingsCommon {...this.props} ref={this.handleRef as any} />;
  }
}
