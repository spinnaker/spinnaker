import React from 'react';
import { Field, FormikProps } from 'formik';

import { HelpField } from '@spinnaker/core';

import { IAmazonNetworkLoadBalancerUpsertCommand } from 'amazon/domain';

export interface INLBAdvancedSettingsProps {
  formik: FormikProps<IAmazonNetworkLoadBalancerUpsertCommand>;
}

export class NLBAdvancedSettings extends React.Component<INLBAdvancedSettingsProps> {
  public render() {
    const { values } = this.props.formik;
    return (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          <b>Protection</b> <HelpField id="loadBalancer.advancedSettings.deletionProtection" />
        </div>
        <div className="col-md-7 checkbox">
          <label>
            <Field type="checkbox" name="deletionProtection" checked={values.deletionProtection} />
            Enable deletion protection
          </label>
        </div>
      </div>
    );
  }
}
