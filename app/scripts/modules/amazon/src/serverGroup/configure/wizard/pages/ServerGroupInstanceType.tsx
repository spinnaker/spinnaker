import { FormikErrors, FormikProps } from 'formik';
import React from 'react';

import { IWizardPageComponent, NgReact } from '@spinnaker/core';
import { AWSProviderSettings } from '../../../../aws.settings';

import { CpuCreditsToggle } from '../instanceType/CpuCreditsToggle';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupInstanceTypeProps {
  formik: FormikProps<IAmazonServerGroupCommand>;
}

export interface IServerGroupInstanceTypeState {
  newInstanceType?: string;
  newProfileType?: string;
}

export class ServerGroupInstanceType
  extends React.Component<IServerGroupInstanceTypeProps, IServerGroupInstanceTypeState>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  constructor(props: IServerGroupInstanceTypeProps) {
    super(props);
    this.state = {
      newInstanceType: undefined,
      newProfileType: undefined,
    };
  }

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
    this.setState({ newProfileType: _profile, newInstanceType: undefined });
  };

  private instanceTypeChanged = (type: string) => {
    const { values } = this.props.formik;
    values.instanceTypeChanged(values);
    this.props.formik.setFieldValue('instanceType', type);
    this.setState({ newInstanceType: type, newProfileType: undefined });
  };

  private setUnlimitedCpuCredits = (unlimitedCpuCredits: boolean | undefined) => {
    if (this.props.formik.values.unlimitedCpuCredits !== unlimitedCpuCredits) {
      this.props.formik.setFieldValue('unlimitedCpuCredits', unlimitedCpuCredits);
      this.setState({});
    }
  };

  public render() {
    const { values } = this.props.formik;

    const { InstanceArchetypeSelector, InstanceTypeSelector } = NgReact;
    const showTypeSelector = !!(values.viewState.disableImageSelection || values.amiName);

    const isLaunchTemplatesEnabled = AWSProviderSettings.serverGroups?.enableLaunchTemplates;
    const isCpuCreditsEnabled = AWSProviderSettings.serverGroups?.enableCpuCredits;

    if (showTypeSelector && values) {
      return (
        <div className="container-fluid form-horizontal">
          <div className="row">
            <InstanceArchetypeSelector
              command={values}
              onTypeChanged={this.instanceTypeChanged}
              onProfileChanged={this.instanceProfileChanged}
            />
            <div style={{ padding: '0 15px' }}>
              {values.viewState.instanceProfile && values.viewState.instanceProfile !== 'custom' && (
                <InstanceTypeSelector command={values} onTypeChanged={this.instanceTypeChanged} />
              )}
            </div>
          </div>
          {isLaunchTemplatesEnabled && isCpuCreditsEnabled && (
            <div className="row">
              <CpuCreditsToggle
                command={values}
                newInstanceType={this.state.newInstanceType}
                newProfileType={this.state.newProfileType}
                setUnlimitedCpuCredits={this.setUnlimitedCpuCredits}
              />
            </div>
          )}
        </div>
      );
    }

    return <h5 className="text-center">Please select an image.</h5>;
  }
}
