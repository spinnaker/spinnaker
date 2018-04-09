import * as React from 'react';
import { Overridable, IOverridableProps } from 'core/overrideRegistry';

export interface ISecurityGroupDetailsProps extends IOverridableProps {}

@Overridable('securityGroup.details')
export class SecurityGroupDetails extends React.Component<ISecurityGroupDetailsProps> {
  public render() {
    return <h3>Security Group Details</h3>;
  }
}
