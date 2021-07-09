import { UISref } from '@uirouter/react';
import { UIRouterContextComponent } from '@uirouter/react-hybrid';
import { sortBy } from 'lodash';
import React from 'react';

import {
  AccountService,
  Application,
  CollapsibleSection,
  FirewallLabels,
  ISecurityGroup,
  ReactInjector,
} from '@spinnaker/core';
import { ITitusServerGroupView } from '../../domain';
import { TitusReactInjector } from '../../reactShims';

export interface ITitusServerGroupDetailsSectionProps {
  app: Application;
  serverGroup: ITitusServerGroupView;
}

export interface ISecurityGroupsDetailsSectionState {
  securityGroups?: ISecurityGroup[];
}

export class TitusSecurityGroupsDetailsSection extends React.Component<
  ITitusServerGroupDetailsSectionProps,
  ISecurityGroupsDetailsSectionState
> {
  constructor(props: ITitusServerGroupDetailsSectionProps) {
    super(props);
    this.state = {};
  }

  public componentDidMount() {
    this.configureSecurityGroups(this.props);
  }

  private configureSecurityGroups(props: ITitusServerGroupDetailsSectionProps): void {
    const { app, serverGroup } = props;
    const { region } = serverGroup;
    if (app.securityGroupsIndex && serverGroup.accountDetails) {
      const securityGroups = serverGroup.securityGroups.map((sgId) =>
        TitusReactInjector.titusSecurityGroupReader.resolveIndexedSecurityGroup(
          app.securityGroupsIndex,
          { account: serverGroup.accountDetails.awsAccount, region },
          sgId,
        ),
      );
      this.setState({ securityGroups });
    } else {
      AccountService.listAllAccounts('titus').then((accounts) => {
        const titusAccount = accounts.find((a) => a.name === serverGroup.account);
        if (titusAccount && titusAccount.awsAccount) {
          ReactInjector.securityGroupReader.getAllSecurityGroups().then((allSecurityGroups) => {
            const regionalGroups = allSecurityGroups[titusAccount.awsAccount]['aws'][region];
            const securityGroups = serverGroup.securityGroups
              .map((sgId) => regionalGroups.find((rg) => rg.id === sgId))
              .filter((g) => g)
              .map((sg) => ({
                account: titusAccount.awsAccount,
                name: sg.name,
                id: sg.id,
                vpcId: sg.vpcId,
              }));
            this.setState({ securityGroups });
          });
        }
      });
    }
  }

  public componentWillReceiveProps(nextProps: ITitusServerGroupDetailsSectionProps): void {
    this.configureSecurityGroups(nextProps);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const { securityGroups } = this.state;
    // prevent a reflow of the details panel when the security groups actually load by rendering a placeholder
    const initializing = !securityGroups && (serverGroup.securityGroups || []).length;

    return (
      <CollapsibleSection heading={FirewallLabels.get('Firewalls')} outerDivClassName="">
        <ul>
          {initializing && serverGroup.securityGroups.map((sgId) => <li key={sgId}>...</li>)}
          <UIRouterContextComponent>
            <>
              {sortBy(securityGroups, 'name').map((securityGroup) => (
                <li key={securityGroup.name}>
                  <UISref
                    to="^.firewallDetails"
                    params={{
                      name: securityGroup.name,
                      accountId: securityGroup.account,
                      region: serverGroup.region,
                      vpcId: securityGroup.vpcId,
                      provider: 'aws',
                    }}
                  >
                    <a>
                      {securityGroup.name} ({securityGroup.id})
                    </a>
                  </UISref>
                </li>
              ))}
            </>
          </UIRouterContextComponent>
        </ul>
      </CollapsibleSection>
    );
  }
}
