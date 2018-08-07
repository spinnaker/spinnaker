import * as React from 'react';
import { FormikProps } from 'formik';

import { IWizardPageProps, wizardPage, NgReact } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

class ServerGroupInstanceTypeImpl extends React.Component<IWizardPageProps & FormikProps<IAmazonServerGroupCommand>> {
  public static LABEL = 'Instance Type';

  public validate(values: IAmazonServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

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
    this.props.values.instanceTypeChanged(this.props.values);
    this.props.setFieldValue('instanceType', type);
  };

  public render() {
    const { values } = this.props;
    const showTypeSelector = !!(values.viewState.disableImageSelection || values.amiName);

    const { InstanceArchetypeSelector, InstanceTypeSelector } = NgReact;

    if (showTypeSelector && values) {
      return (
        <div>
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
