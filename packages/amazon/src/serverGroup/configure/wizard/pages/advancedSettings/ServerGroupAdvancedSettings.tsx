import { FormikProps } from 'formik';
import React from 'react';

import { Application, IWizardPageComponent } from '@spinnaker/core';

import { ServerGroupAdvancedSettingsInner } from './ServerGroupAdvancedSettingsInner';
import { IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';

export interface IServerGroupAdvancedSettingsProps {
  app: Application;
  formik: FormikProps<IAmazonServerGroupCommand>;
}

export class ServerGroupAdvancedSettings
  extends React.Component<IServerGroupAdvancedSettingsProps>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  private ref: any = React.createRef();

  public validate(values: IAmazonServerGroupCommand) {
    if (this.ref && this.ref.current) {
      return this.ref.current.validate(values);
    }
    return {};
  }

  public render() {
    const { app, formik } = this.props;
    return <ServerGroupAdvancedSettingsInner formik={formik} app={app} ref={this.ref} />;
  }
}
