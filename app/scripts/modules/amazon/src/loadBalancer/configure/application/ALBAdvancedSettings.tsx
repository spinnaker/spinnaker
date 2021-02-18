import React from 'react';

import { CheckboxInput, FormikFormField, HelpField, NumberInput } from '@spinnaker/core';

export interface IALBAdvancedSettingsProps {
  isInternal: boolean;
}

export const ALBAdvancedSettings = React.forwardRef<HTMLDivElement, IALBAdvancedSettingsProps>((props, ref) => (
  <div ref={ref}>
    <FormikFormField
      name="idleTimeout"
      label="Idle Timeout"
      help={<HelpField id="loadBalancer.advancedSettings.idleTimeout" />}
      input={(inputProps) => <NumberInput {...inputProps} min={0} />}
    />

    <FormikFormField
      name="deletionProtection"
      label="Protection"
      help={<HelpField id="loadBalancer.advancedSettings.deletionProtection" />}
      input={(inputProps) => <CheckboxInput {...inputProps} text="Enable delete protection" />}
    />

    <FormikFormField
      name="dualstack"
      label="Dualstack"
      help={<HelpField id="loadBalancer.advancedSettings.albIpAddressType" />}
      input={(inputProps) => (
        <CheckboxInput {...inputProps} text="Assign Ipv4 and IPv6" disabled={Boolean(props.isInternal)} />
      )}
    />
  </div>
));
