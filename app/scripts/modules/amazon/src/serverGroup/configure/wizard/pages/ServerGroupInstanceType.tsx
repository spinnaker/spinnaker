import * as React from 'react';
import { FormikErrors } from 'formik';

import { IWizardPageProps, wizardPage, NgReact } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export type IServerGroupInstanceTypeProps = IWizardPageProps<IAmazonServerGroupCommand>;

class ServerGroupInstanceTypeImpl extends React.Component<IServerGroupInstanceTypeProps> {
  public static LABEL = 'Instance Type';

  public validate(values: IAmazonServerGroupCommand) {
    const errors: FormikErrors<IAmazonServerGroupCommand> = {};

    if (!values.instanceType) {
      errors.instanceType = 'Instance Type required.';
    }

    return errors;
  }

  private instanceProfileChanged = (_profile: string) => {
    // Instance profile is already set on values.viewState, so just use that value.
    // Once angular is gone from this component tree, we can move all the viewState stuff
    // into react state
    this.setState({});
  };

  private instanceTypeChanged = (type: string) => {
    const { values } = this.props.formik;
    values.instanceTypeChanged(values);
    this.props.formik.setFieldValue('instanceType', type);
  };

  public render() {
    const { values } = this.props.formik;
    const showTypeSelector = !!(values.viewState.disableImageSelection || values.amiName);

    const { InstanceArchetypeSelector, InstanceTypeSelector } = NgReact;

    if (showTypeSelector && values) {
      return (
        <div className="container-fluid form-horizontal">
          <InstanceArchetypeSelector
            command={values}
            onTypeChanged={this.instanceTypeChanged}
            onProfileChanged={this.instanceProfileChanged}
          />
          <div style={{ padding: '0 15px' }}>
            {values.viewState.instanceProfile &&
              values.viewState.instanceProfile !== 'custom' && (
                <InstanceTypeSelector command={values} onTypeChanged={this.instanceTypeChanged} />
              )}
          </div>
        </div>
      );
    }

    return <h5 className="text-center">Please select an image.</h5>;
  }
}

export const ServerGroupInstanceType = wizardPage(ServerGroupInstanceTypeImpl);
