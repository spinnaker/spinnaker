import type { FormikErrors } from 'formik';
import React from 'react';

import type { IWizardPageComponent } from '@spinnaker/core';
import { Overridable } from '@spinnaker/core';

import type { IServerGroupAdvancedSettingsProps } from './ServerGroupAdvancedSettings';
import { ServerGroupAdvancedSettingsCommon } from './ServerGroupAdvancedSettingsCommon';
import type { IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';

@Overridable('aws.serverGroup.advancedSettings')
export class ServerGroupAdvancedSettingsInner
  extends React.Component<IServerGroupAdvancedSettingsProps>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  private validators = new Map();

  public validate = (values: IAmazonServerGroupCommand) => {
    const errors: FormikErrors<IAmazonServerGroupCommand> = {};

    this.validators.forEach((validator) => {
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
