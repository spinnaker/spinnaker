import { UISref } from '@uirouter/react';
import { chain, find, sortBy } from 'lodash';
import React from 'react';

import {
  CollapsibleSection,
  confirmNotManaged,
  FirewallLabels,
  ISecurityGroup,
  ISecurityGroupsByAccount,
  ModalInjector,
} from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { AwsSecurityGroupReader } from '../../../securityGroup/securityGroup.reader';

export interface ISecurityGroupsDetailsSectionState {
  securityGroups: ISecurityGroup[];
}

export class SecurityGroupsDetailsSection extends React.Component<
  IAmazonServerGroupDetailsSectionProps,
  ISecurityGroupsDetailsSectionState
> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);

    this.state = { securityGroups: this.getSecurityGroups(props) };
  }

  private tryFindingSecurityGroupInIndex(
    index: ISecurityGroupsByAccount,
    account: string,
    region: string,
    securityGroupId: string,
  ): ISecurityGroup {
    try {
      return AwsSecurityGroupReader.resolveIndexedSecurityGroup(index, { account, region }, securityGroupId);
    } catch (e) {
      return undefined;
    }
  }

  private getSecurityGroups(props: IAmazonServerGroupDetailsSectionProps): ISecurityGroup[] {
    let securityGroups: ISecurityGroup[];
    const { app, serverGroup } = props;

    if (serverGroup.securityGroups && serverGroup.securityGroups.length) {
      securityGroups = chain(serverGroup.securityGroups)
        .map((id: string) => {
          return (
            find(app.securityGroups.data, { accountName: serverGroup.account, region: serverGroup.region, id }) ||
            find(app.securityGroups.data, { accountName: serverGroup.account, region: serverGroup.region, name: id }) ||
            this.tryFindingSecurityGroupInIndex(
              app['securityGroupsIndex'],
              serverGroup.account,
              serverGroup.region,
              id,
            ) || { id, name: id } // Last resort fallback so that security groups do not get removed (upon editing) just because deck couldn't find them
          );
        })
        .compact()
        .value();
    }

    return securityGroups;
  }

  private updateSecurityGroups = (): void => {
    const { app, serverGroup } = this.props;
    confirmNotManaged(serverGroup, app).then(
      (notManaged) =>
        notManaged &&
        ModalInjector.modalService.open({
          templateUrl: require('../securityGroup/editSecurityGroups.modal.html'),
          controller: 'EditSecurityGroupsCtrl as $ctrl',
          resolve: {
            application: () => app,
            serverGroup: () => serverGroup,
            securityGroups: () => this.state.securityGroups,
          },
        }),
    );
  };

  public componentWillReceiveProps(nextProps: IAmazonServerGroupDetailsSectionProps): void {
    this.setState({ securityGroups: this.getSecurityGroups(nextProps) });
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const { securityGroups } = this.state;

    return (
      <CollapsibleSection heading={FirewallLabels.get('Firewalls')}>
        <ul>
          {sortBy(securityGroups, 'name').map((securityGroup) => (
            <li key={securityGroup.name}>
              <UISref
                to="^.firewallDetails"
                params={{
                  name: securityGroup.name,
                  accountId: securityGroup.accountName,
                  region: serverGroup.region,
                  vpcId: serverGroup.vpcId,
                  provider: serverGroup.type,
                }}
              >
                <a>
                  {securityGroup.name} ({securityGroup.id})
                </a>
              </UISref>
            </li>
          ))}
        </ul>
        {serverGroup.vpcId && (
          <a className="clickable" onClick={this.updateSecurityGroups}>
            Edit {FirewallLabels.get('Firewalls')}
          </a>
        )}
      </CollapsibleSection>
    );
  }
}
