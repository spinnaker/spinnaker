import React from 'react';

import { FirewallLabels } from './label/FirewallLabels';
import { IOverridableProps, Overridable } from '../overrideRegistry';

export interface ISecurityGroupDetailsProps extends IOverridableProps {}

@Overridable('securityGroup.details')
export class SecurityGroupDetails extends React.Component<ISecurityGroupDetailsProps> {
  public render() {
    return <h3>{FirewallLabels.get('Firewalls')} Details</h3>;
  }
}
