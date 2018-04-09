import * as React from 'react';
import { Field, FormikProps } from 'formik';

import { HelpField, IWizardPageProps, ValidationError, wizardPage } from '@spinnaker/core';

import { IAmazonClassicLoadBalancerUpsertCommand } from 'amazon/domain';

class AdvancedSettingsImpl extends React.Component<
  IWizardPageProps & FormikProps<IAmazonClassicLoadBalancerUpsertCommand>
> {
  public static LABEL = 'Advanced Settings';

  public validate(values: IAmazonClassicLoadBalancerUpsertCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

    if (values.healthTimeout >= values.healthInterval) {
      errors.healthTimeout = 'The health timeout must be less than the health interval.';
    }

    return errors;
  }

  public render() {
    const { errors, values } = this.props;
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
                <ValidationError message={errors.healthTimeout} />
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
