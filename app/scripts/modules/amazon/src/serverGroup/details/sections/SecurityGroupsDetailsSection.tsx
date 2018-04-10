import * as React from 'react';
import { chain, find, sortBy } from 'lodash';
import { BindAll } from 'lodash-decorators';
import { UISref } from '@uirouter/react';

import { CollapsibleSection, ISecurityGroup, ReactInjector } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export interface ISecurityGroupsDetailsSectionState {
  securityGroups: ISecurityGroup[];
}

@BindAll()
export class SecurityGroupsDetailsSection extends React.Component<
  IAmazonServerGroupDetailsSectionProps,
  ISecurityGroupsDetailsSectionState
> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);

    this.state = { securityGroups: this.getSecurityGroups(props) };
  }

  private getSecurityGroups(props: IAmazonServerGroupDetailsSectionProps): ISecurityGroup[] {
    let securityGroups: ISecurityGroup[];
    const { app, serverGroup } = props;
    if (props.serverGroup.launchConfig && serverGroup.launchConfig.securityGroups) {
      securityGroups = chain(serverGroup.launchConfig.securityGroups)
        .map((id: string) => {
          return (
            find(app.securityGroups.data, { accountName: serverGroup.account, region: serverGroup.region, id }) ||
            find(app.securityGroups.data, { accountName: serverGroup.account, region: serverGroup.region, name: id })
          );
        })
        .compact()
        .value();
    }

    return securityGroups;
  }

  private updateSecurityGroups(): void {
    ReactInjector.modalService.open({
      templateUrl: require('../securityGroup/editSecurityGroups.modal.html'),
      controller: 'EditSecurityGroupsCtrl as $ctrl',
      resolve: {
        application: () => this.props.app,
        serverGroup: () => this.props.serverGroup,
        securityGroups: () => this.state.securityGroups,
      },
    });
  }

  public componentWillReceiveProps(nextProps: IAmazonServerGroupDetailsSectionProps): void {
    this.setState({ securityGroups: this.getSecurityGroups(nextProps) });
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const { securityGroups } = this.state;

    return (
      <CollapsibleSection heading="Security Groups">
        <ul>
          {sortBy(securityGroups, 'name').map(securityGroup => (
            <li key={securityGroup.name}>
              <UISref
                to="^.securityGroupDetails"
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
            Edit Security Groups
          </a>
        )}
      </CollapsibleSection>
    );
  }
}
