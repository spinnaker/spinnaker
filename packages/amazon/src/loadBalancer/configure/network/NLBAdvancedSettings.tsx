import React from 'react';

import { CheckboxInput, FormikFormField, HelpField } from '@spinnaker/core';

export interface INLBAdvancedSettingsProps {
  showDualstack: boolean;
}

export const NLBAdvancedSettings = React.forwardRef<HTMLDivElement, INLBAdvancedSettingsProps>((props, ref) => (
  <div ref={ref}>
    <FormikFormField
      name="deletionProtection"
      label="Protection"
      help={<HelpField id="loadBalancer.advancedSettings.deletionProtection" />}
      input={(inputProps) => <CheckboxInput {...inputProps} text="Enable deletion protection" />}
    />

    <FormikFormField
      name="loadBalancingCrossZone"
      label="Cross-Zone Load Balancing"
      help={<HelpField id="loadBalancer.advancedSettings.loadBalancingCrossZone" />}
      input={(inputProps) => <CheckboxInput {...inputProps} text="Distribute traffic across zones" />}
    />

    {props.showDualstack && (
      <FormikFormField
        name="dualstack"
        label="Dualstack"
        help={<HelpField id="loadBalancer.advancedSettings.nlbIpAddressType" />}
        input={(inputProps) => <CheckboxInput {...inputProps} text="Assign Ipv4 and IPv6" />}
      />
    )}
  </div>
));
