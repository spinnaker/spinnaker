import { FormikProps } from 'formik';
import React from 'react';

import { FormikFormField, HelpField, NumberInput, Validators } from '@spinnaker/core';
import { IAmazonClassicLoadBalancerUpsertCommand } from '../../../domain';

import './AdvancedSettings.css';

export interface IAdvancedSettingsProps {
  formik: FormikProps<IAmazonClassicLoadBalancerUpsertCommand>;
}

export class AdvancedSettings extends React.Component<IAdvancedSettingsProps> {
  public render() {
    const { values } = this.props.formik;
    const { maxValue } = Validators;
    return (
      <div className="form-group AmazonLoadBalancer-AdvancedSettings">
        <FormikFormField
          name="healthTimeout"
          label="Timeout"
          required={true}
          help={<HelpField id="loadBalancer.advancedSettings.healthTimeout" />}
          input={(props) => <NumberInput {...props} min={0} max={values.healthInterval} />}
          validate={maxValue(values.healthInterval, 'Timeout must be less than the health interval.')}
        />

        <FormikFormField
          name="healthInterval"
          label="Interval"
          required={true}
          help={<HelpField id="loadBalancer.advancedSettings.healthInterval" />}
          input={(props) => <NumberInput {...props} min={0} />}
        />

        <FormikFormField
          name="healthyThreshold"
          label="Healthy Threshold"
          required={true}
          help={<HelpField id="loadBalancer.advancedSettings.healthyThreshold" />}
          input={(props) => <NumberInput {...props} min={0} />}
        />

        <FormikFormField
          name="unhealthyThreshold"
          label="Unhealthy Threshold"
          required={true}
          help={<HelpField id="loadBalancer.advancedSettings.unhealthyThreshold" />}
          input={(props) => <NumberInput {...props} min={0} />}
        />

        <FormikFormField
          name="idleTimeout"
          label="Idle Timeout"
          required={true}
          help={<HelpField id="loadBalancer.advancedSettings.idleTimeout" />}
          input={(props) => <NumberInput {...props} min={0} />}
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
