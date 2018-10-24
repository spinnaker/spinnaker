import * as React from 'react';
import { Field, FormikErrors } from 'formik';

import { HelpField, IWizardPageProps, wizardPage } from '@spinnaker/core';

import { IAmazonApplicationLoadBalancerUpsertCommand } from 'amazon/domain';

export type IALBAdvancedSettingsProps = IWizardPageProps<IAmazonApplicationLoadBalancerUpsertCommand>;

class ALBAdvancedSettingsImpl extends React.Component<IALBAdvancedSettingsProps> {
  public static LABEL = 'Advanced Settings';

  public validate() {
    const errors = {} as FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand>;
    return errors;
  }

  public render() {
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <span>
              <b>Idle Timeout</b> <HelpField id="loadBalancer.advancedSettings.idleTimeout" />
            </span>
          </div>
          <div className="col-md-4">
            <Field className="form-control input-sm" type="number" min="0" name="idleTimeout" />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Protection</b> <HelpField id="loadBalancer.advancedSettings.deletionProtection" />
          </div>
          <div className="col-md-7 checkbox">
            <label>
              <Field type="checkbox" name="deletionProtection" />
              Enable deletion protection
            </label>
          </div>
        </div>
      </div>
    );
  }
}

export const ALBAdvancedSettings = wizardPage(ALBAdvancedSettingsImpl);
