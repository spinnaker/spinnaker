import React from 'react';

import { FirewallLabels } from './label/FirewallLabels';
import type { IOverridableProps } from '../overrideRegistry';
import { Overridable } from '../overrideRegistry';

export interface ISecurityGroupDetailsProps extends IOverridableProps {}

@Overridable('securityGroup.details')
export class SecurityGroupDetails extends React.Component<ISecurityGroupDetailsProps> {
  public render() {
    return <h3>{FirewallLabels.get('Firewalls')} Details</h3>;
  }
}
