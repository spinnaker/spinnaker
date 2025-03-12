import React from 'react';

import { CheckboxInput, FormikFormField, HelpField, NumberInput } from '@spinnaker/core';

export const ALBAdvancedSettings = React.forwardRef<HTMLDivElement, {}>((_, ref) => (
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
      input={(inputProps) => <CheckboxInput {...inputProps} text="Assign Ipv4 and IPv6" />}
    />
  </div>
));
