import React from 'react';
import { Overridable } from '@spinnaker/core';
import { ISecurityGroupDetail } from '@spinnaker/core';

export interface IAdditionalIpRulesProps {
  securityGroupDetails: ISecurityGroupDetail;
  ctrl: any;
  scope: any;
}

@Overridable('aws.securityGroup.details.custom')
export class SecurityGroupDetailsCustom extends React.Component<IAdditionalIpRulesProps> {
  public render(): any {
    return null;
  }
}
