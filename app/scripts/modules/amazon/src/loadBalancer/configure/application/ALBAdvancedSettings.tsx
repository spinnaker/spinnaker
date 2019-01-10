import * as React from 'react';

import { FormikFormField, CheckboxInput, NumberInput, HelpField } from '@spinnaker/core';

export class ALBAdvancedSettings extends React.Component {
  public render() {
    return (
      <div>
        <FormikFormField
          name="idleTimeout"
          label="Idle Timeout"
          help={<HelpField id="loadBalancer.advancedSettings.idleTimeout" />}
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
