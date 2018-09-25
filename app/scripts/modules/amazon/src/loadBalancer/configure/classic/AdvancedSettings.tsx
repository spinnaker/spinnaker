import * as React from 'react';
import { Field, FormikErrors } from 'formik';

import { HelpField, IWizardPageProps, ValidationMessage, wizardPage } from '@spinnaker/core';

import { IAmazonClassicLoadBalancerUpsertCommand } from 'amazon/domain';

export type IAdvancedSettingsProps = IWizardPageProps<IAmazonClassicLoadBalancerUpsertCommand>;

class AdvancedSettingsImpl extends React.Component<IAdvancedSettingsProps> {
  public static LABEL = 'Advanced Settings';

  public validate(values: IAmazonClassicLoadBalancerUpsertCommand) {
    const errors: FormikErrors<IAmazonClassicLoadBalancerUpsertCommand> = {};

    if (values.healthTimeout >= values.healthInterval) {
      errors.healthTimeout = 'The health timeout must be less than the health interval.';
    }

    return errors;
  }

  public render() {
    const { errors, values } = this.props.formik;
    return (
      <div className="form-group">
        <div className="col-md-8 nest">
          <div className="form-group">
            <div className="col-md-6 sm-label-right">
              <span>
                <b>Timeout</b> <HelpField id="loadBalancer.advancedSettings.healthTimeout" />
              </span>
            </div>
            <div className="col-md-4">
              <Field
                className="form-control input-sm"
                type="number"
                name="healthTimeout"
                min={0}
                max={values.healthInterval - 1}
              />
            </div>
            {errors.healthTimeout && (
              <div className="col-md-12 col-md-offset-6">
                <ValidationMessage type="error" message={errors.healthTimeout} />
              </div>
            )}
          </div>

          <div className="form-group">
            <div className="col-md-6 sm-label-right">
              <span>
                <b>Interval</b> <HelpField id="loadBalancer.advancedSettings.healthInterval" />
              </span>
            </div>
            <div className="col-md-4">
              <Field className="form-control input-sm" type="number" min="0" name="healthInterval" />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-6 sm-label-right">
              <span>
                <b>Healthy Threshold</b> <HelpField id="loadBalancer.advancedSettings.healthyThreshold" />
              </span>
            </div>
            <div className="col-md-4">
              <Field className="form-control input-sm" type="number" min="0" name="healthyThreshold" />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-6 sm-label-right">
              <span>
                <b>Unhealthy Threshold</b> <HelpField id="loadBalancer.advancedSettings.unhealthyThreshold" />
              </span>
            </div>
            <div className="col-md-4">
              <Field className="form-control input-sm" type="number" min="0" name="unhealthyThreshold" />
            </div>
          </div>
        </div>
        <div className="col-md-12">
          <p>
            Additional configuration options (idle timeout, cross-zone load balancing, session stickiness, access logs)
            are available via the AWS console.
          </p>
        </div>
      </div>
    );
  }
}

export const AdvancedSettings = wizardPage(AdvancedSettingsImpl);
