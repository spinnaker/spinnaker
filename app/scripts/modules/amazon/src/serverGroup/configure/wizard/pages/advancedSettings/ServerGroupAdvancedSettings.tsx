import * as React from 'react';
import { FormikProps } from 'formik';

import { IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from 'amazon/serverGroup/configure/serverGroupConfiguration.service';
import { ServerGroupAdvancedSettingsInner } from './ServerGroupAdvancedSettingsInner';

export interface IServerGroupAdvancedSettingsProps {
  app: Application;
}

class ServerGroupAdvancedSettingsImpl extends React.Component<
  IServerGroupAdvancedSettingsProps & IWizardPageProps & FormikProps<IAmazonServerGroupCommand>
> {
  public static LABEL = 'Advanced Settings';
  private ref: any = React.createRef();

  public validate = (values: IAmazonServerGroupCommand): { [key: string]: string } => {
    if (this.ref && this.ref.current) {
      return this.ref.current.validate(values);
    }
    return {};
  };

  public render() {
    return <ServerGroupAdvancedSettingsInner {...this.props} ref={this.ref} />;
  }
}

export const ServerGroupAdvancedSettings = wizardPage<IServerGroupAdvancedSettingsProps>(
  ServerGroupAdvancedSettingsImpl,
);
