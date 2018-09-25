import * as React from 'react';
import { FormikErrors } from 'formik';

import { Overridable } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from 'amazon/serverGroup/configure/serverGroupConfiguration.service';
import { ServerGroupAdvancedSettingsCommon } from './ServerGroupAdvancedSettingsCommon';
import { IServerGroupAdvancedSettingsProps } from './ServerGroupAdvancedSettings';

@Overridable('aws.serverGroup.advancedSettings')
export class ServerGroupAdvancedSettingsInner extends React.Component<IServerGroupAdvancedSettingsProps> {
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
    return <ServerGroupAdvancedSettingsCommon {...this.props} ref={this.handleRef as any} />;
  }
}
