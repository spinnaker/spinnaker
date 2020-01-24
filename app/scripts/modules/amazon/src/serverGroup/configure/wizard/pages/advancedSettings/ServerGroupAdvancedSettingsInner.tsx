import React from 'react';
import { FormikErrors } from 'formik';

import { IWizardPageComponent, Overridable } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';
import { ServerGroupAdvancedSettingsCommon } from './ServerGroupAdvancedSettingsCommon';
import { IServerGroupAdvancedSettingsProps } from './ServerGroupAdvancedSettings';

@Overridable('aws.serverGroup.advancedSettings')
export class ServerGroupAdvancedSettingsInner extends React.Component<IServerGroupAdvancedSettingsProps>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  private validators = new Map();

  public validate = (values: IAmazonServerGroupCommand) => {
    const errors: FormikErrors<IAmazonServerGroupCommand> = {};

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
    const { formik, app } = this.props;
    return <ServerGroupAdvancedSettingsCommon formik={formik} app={app} ref={this.handleRef as any} />;
  }
}
