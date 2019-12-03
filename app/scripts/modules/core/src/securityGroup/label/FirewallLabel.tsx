import React from 'react';

import { FirewallLabels } from './FirewallLabels';

export interface IFirewallLabel {
  label: string;
}

export function FirewallLabel(props: IFirewallLabel) {
  return <span>{FirewallLabels.get(props.label)}</span>;
}
