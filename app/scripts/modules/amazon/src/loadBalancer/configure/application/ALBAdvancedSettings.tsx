import * as React from 'react';
import { FormikErrors } from 'formik';

import {
  Validation,
  FormikFormField,
  CheckboxInput,
  NumberInput,
  HelpField,
  IWizardPageProps,
  wizardPage,
} from '@spinnaker/core';

import { IAmazonApplicationLoadBalancerUpsertCommand } from 'amazon/domain';

export type IALBAdvancedSettingsProps = IWizardPageProps<IAmazonApplicationLoadBalancerUpsertCommand>;

class ALBAdvancedSettingsImpl extends React.Component<IALBAdvancedSettingsProps> {
  public static LABEL = 'Advanced Settings';

  public validate(): FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand> {
    return {};
  }

  public render() {
    return (
      <div>
        <FormikFormField
          name="idleTimeout"
          label="Idle Timeout"
          help={<HelpField id="loadBalancer.advancedSettings.idleTimeout" />}
          validate={Validation.minValue(0)}
          input={props => <NumberInput {...props} min={0} />}
        />

        <FormikFormField
          name="deletionProtection"
          label="Protection"
          help={<HelpField id="loadBalancer.advancedSettings.deletionProtection" />}
          input={props => <CheckboxInput {...props} text="Enable delete protection" />}
        />
      </div>
    );
  }
}

export const ALBAdvancedSettings = wizardPage(ALBAdvancedSettingsImpl);
