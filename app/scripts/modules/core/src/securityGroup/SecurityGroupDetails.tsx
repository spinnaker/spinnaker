import React from 'react';
import { Overridable, IOverridableProps } from 'core/overrideRegistry';
import { FirewallLabels } from './label/FirewallLabels';

export interface ISecurityGroupDetailsProps extends IOverridableProps {}

@Overridable('securityGroup.details')
export class SecurityGroupDetails extends React.Component<ISecurityGroupDetailsProps> {
  public render() {
    return <h3>{FirewallLabels.get('Firewalls')} Details</h3>;
  }
}
