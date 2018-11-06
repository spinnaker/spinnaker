import * as React from 'react';
import { FormikErrors } from 'formik';

import { IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from 'amazon/serverGroup/configure/serverGroupConfiguration.service';
import { ServerGroupAdvancedSettingsInner } from './ServerGroupAdvancedSettingsInner';

export interface IServerGroupAdvancedSettingsProps extends IWizardPageProps<IAmazonServerGroupCommand> {
  app: Application;
}

class ServerGroupAdvancedSettingsImpl extends React.Component<IServerGroupAdvancedSettingsProps> {
  public static LABEL = 'Advanced Settings';
  private ref: any = React.createRef();

  public validate = (values: IAmazonServerGroupCommand) => {
    if (this.ref && this.ref.current) {
      return this.ref.current.validate(values);
    }
    return {} as FormikErrors<IAmazonServerGroupCommand>;
  };

  public render() {
    return <ServerGroupAdvancedSettingsInner {...this.props} ref={this.ref} />;
  }
}

export const ServerGroupAdvancedSettings = wizardPage(ServerGroupAdvancedSettingsImpl);
