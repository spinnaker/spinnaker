import { chain, xor } from 'lodash';
import { $q } from 'ngimport';
import React from 'react';

import { SecurityGroupSelector, ServerGroupSecurityGroupsRemoved, VpcReader } from '@spinnaker/amazon';
import {
  AccountService,
  AccountTag,
  FirewallLabels,
  IAccountDetails,
  IAggregatedAccounts,
  ISecurityGroup,
  IVpc,
  ReactInjector,
} from '@spinnaker/core';

export interface ITitusSecurityGroupPickerProps {
  account: string;
  region: string;
  command: any;
  groupsToEdit: string[];
  hideLabel: boolean;
  amazonAccount: string;
  onChange: (groups: string[]) => void;
}

export interface ITitusSecurityGroupPickerState {
  availableGroups: ISecurityGroup[];
  removedGroups: string[];
  loaded: boolean;
}

export class TitusSecurityGroupPicker extends React.Component<
  ITitusSecurityGroupPickerProps,
  ITitusSecurityGroupPickerState
> {
  public state: ITitusSecurityGroupPickerState = {
    availableGroups: [],
    removedGroups: [],
    loaded: false,
  };

  private credentials: IAggregatedAccounts = {};
  private vpcs: IVpc[] = [];
  private securityGroups: any = {};

  constructor(props: ITitusSecurityGroupPickerProps) {
    super(props);
  }

  private getCredentials(): IAccountDetails {
    return this.credentials[this.props.command.credentials];
  }

  private getAwsAccount(): string {
    return this.getCredentials().awsAccount;
  }

  private getRegion(): string {
    return this.props.command.region || (this.props.command.cluster ? this.props.command.cluster.region : null);
  }

  private getVpcId(): string {
    const credentials = this.getCredentials();
    const match = this.vpcs.find(
      (vpc) =>
        vpc.name === credentials.awsVpc &&
        vpc.account === credentials.awsAccount &&
        vpc.region === this.getRegion() &&
        vpc.cloudProvider === 'aws',
    );
    return match ? match.id : null;
  }

  private getRegionalSecurityGroups(): ISecurityGroup[] {
    const newSecurityGroups: any = this.securityGroups[this.getAwsAccount()] || { aws: {} };
    return chain<ISecurityGroup>(newSecurityGroups.aws[this.getRegion()])
      .filter({ vpcId: this.getVpcId() })
      .sortBy('name')
      .value();
  }

  public refreshSecurityGroups(skipCommandReconfiguration?: boolean) {
    return ReactInjector.cacheInitializer.refreshCache('securityGroups').then(() => {
      return ReactInjector.securityGroupReader.getAllSecurityGroups().then((securityGroups) => {
        this.securityGroups = securityGroups;
        if (!skipCommandReconfiguration) {
          this.configureSecurityGroupOptions();
        }
      });
    });
  }

  private configureSecurityGroupOptions(): void {
    let availableGroups = this.getRegionalSecurityGroups();
    const oldAvailableGroups = this.state.availableGroups.length ? this.state.availableGroups : availableGroups;
    let removedGroups: string[];

    let groupsToEdit = this.props.groupsToEdit;
    if (availableGroups && this.props.groupsToEdit) {
      // not initializing - we are actually changing groups
      const oldGroupNames: string[] = groupsToEdit.map((groupId: string) => {
        const match = oldAvailableGroups.find((o) => o.id === groupId);
        return match ? match.name : groupId;
      });

      const matchedGroups: ISecurityGroup[] = oldGroupNames
        .map((groupId: string) => {
          const securityGroup: any = availableGroups.find((o) => o.id === groupId || o.name === groupId);
          return securityGroup ? securityGroup.name : null;
        })
        .map((groupName: string) => availableGroups.find((g) => g.name === groupName))
        .filter((group: any) => group);

      const matchedGroupNames: string[] = matchedGroups.map((g) => g.name);
      removedGroups = xor(oldGroupNames, matchedGroupNames);
      groupsToEdit = matchedGroups.map((g) => g.id);
      this.props.onChange(groupsToEdit);
    }
    availableGroups = availableGroups.sort((a, b) => a.name.localeCompare(b.name));
    this.setState({ availableGroups, loaded: true, removedGroups });
  }

  private clearRemoved = () => {
    this.setState({ removedGroups: [] });
  };

  public componentDidMount() {
    const credentialLoader = AccountService.getCredentialsKeyedByAccount('titus').then(
      (credentials: IAggregatedAccounts) => {
        this.credentials = credentials;
      },
    );
    const groupLoader = ReactInjector.securityGroupReader.getAllSecurityGroups().then((groups) => {
      this.securityGroups = groups;
    });
    const vpcLoader = VpcReader.listVpcs().then((vpcs: IVpc[]) => (this.vpcs = vpcs));
    $q.all([credentialLoader, groupLoader, vpcLoader]).then(() => this.configureSecurityGroupOptions());
  }

  public componentWillReceiveProps(nextProps: ITitusSecurityGroupPickerProps) {
    if (this.props.account !== nextProps.account || this.props.region !== nextProps.region) {
      this.configureSecurityGroupOptions();
    }
  }

  public render() {
    const { command, hideLabel, groupsToEdit, amazonAccount } = this.props;
    const { availableGroups, loaded, removedGroups } = this.state;

    const firewallsLabel = FirewallLabels.get('firewalls');

    if (loaded) {
      return (
        <div className="clearfix">
          <ServerGroupSecurityGroupsRemoved removed={removedGroups} onClear={this.clearRemoved} />
          <SecurityGroupSelector
            command={command}
            hideLabel={hideLabel}
            availableGroups={availableGroups}
            groupsToEdit={groupsToEdit}
            refresh={() => this.refreshSecurityGroups() as any}
            onChange={this.props.onChange}
            helpKey="titus.deploy.securityGroups"
          />

          {amazonAccount && command.credentials !== undefined && (
            <div className={`small ${!hideLabel ? 'col-md-offset-3 col-md-9' : ''}`}>
              Uses {firewallsLabel} from the Amazon account <AccountTag account={amazonAccount} />
            </div>
          )}
        </div>
      );
    }

    return null;
  }
}
