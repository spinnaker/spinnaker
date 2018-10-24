import * as React from 'react';
import { Field, FormikErrors } from 'formik';

import { HelpField, IWizardPageProps, wizardPage } from '@spinnaker/core';

import { IAmazonNetworkLoadBalancerUpsertCommand } from 'amazon/domain';

export type INLBAdvancedSettingsProps = IWizardPageProps<IAmazonNetworkLoadBalancerUpsertCommand>;

class NLBAdvancedSettingsImpl extends React.Component<INLBAdvancedSettingsProps> {
  public static LABEL = 'Advanced Settings';

  public validate() {
    const errors = {} as FormikErrors<IAmazonNetworkLoadBalancerUpsertCommand>;
    return errors;
  }

  public render() {
    return (
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
    );
  }
}

export const NLBAdvancedSettings = wizardPage(NLBAdvancedSettingsImpl);
