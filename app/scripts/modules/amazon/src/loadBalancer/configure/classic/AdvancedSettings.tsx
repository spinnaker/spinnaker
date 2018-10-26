import * as React from 'react';
import { FormikErrors } from 'formik';

import { Validation, FormikFormField, NumberInput, HelpField, IWizardPageProps, wizardPage } from '@spinnaker/core';

import { IAmazonClassicLoadBalancerUpsertCommand } from 'amazon/domain';

import './AdvancedSettings.css';

export type IAdvancedSettingsProps = IWizardPageProps<IAmazonClassicLoadBalancerUpsertCommand>;

class AdvancedSettingsImpl extends React.Component<IAdvancedSettingsProps> {
  public static LABEL = 'Advanced Settings';

  public validate(): FormikErrors<IAmazonClassicLoadBalancerUpsertCommand> {
    return {};
  }

  public render() {
    const { values } = this.props.formik;
    return (
      <div className="form-group AmazonLoadBalancer-AdvancedSettings">
        <FormikFormField
          name="healthTimeout"
          label="Timeout"
          help={<HelpField id="loadBalancer.advancedSettings.healthTimeout" />}
          input={NumberInput}
          validate={[
            Validation.minValue(0, 'Timeout cannot be negative'),
            Validation.maxValue(values.healthInterval, 'Timeout must be less than the health Interval.'),
          ]}
        />

        <FormikFormField
          name="healthInterval"
          label="Interval"
          help={<HelpField id="loadBalancer.advancedSettings.healthInterval" />}
          input={NumberInput}
          validate={Validation.minValue(0, 'Interval cannot be negative')}
        />

        <FormikFormField
          name="healthyThreshold"
          label="Healthy Threshold"
          help={<HelpField id="loadBalancer.advancedSettings.healthyThreshold" />}
          input={NumberInput}
          validate={Validation.minValue(0, 'Healthy Threshold cannot be negative')}
        />

        <FormikFormField
          name="unhealthyThreshold"
          label="Unhealthy Threshold"
          help={<HelpField id="loadBalancer.advancedSettings.unhealthyThreshold" />}
          input={NumberInput}
          validate={Validation.minValue(0, 'Unhealthy Threshold cannot be negative')}
        />

        <FormikFormField
          name="idleTimeout"
          label="Idle Timeout"
          help={<HelpField id="loadBalancer.advancedSettings.idleTimeout" />}
          input={NumberInput}
          validate={Validation.minValue(0, 'Idle Timeout cannot be negative')}
        />

        <div className="col-md-12">
          <p>
            Additional configuration options (cross-zone load balancing, session stickiness, access logs) are available
            via the AWS console.
          </p>
        </div>
      </div>
    );
  }
}

export const AdvancedSettings = wizardPage(AdvancedSettingsImpl);
