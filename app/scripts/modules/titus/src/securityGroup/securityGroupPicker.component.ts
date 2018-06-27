import { module } from 'angular';
import * as _ from 'lodash';
import { Subject, Subscription } from 'rxjs';

import {
  AccountService,
  CACHE_INITIALIZER_SERVICE,
  CacheInitializerService,
  IAccountDetails,
  IAggregatedAccounts,
  ISecurityGroup,
  IVpc,
  SECURITY_GROUP_READER,
  FirewallLabels,
} from '@spinnaker/core';

import { VpcReader } from '@spinnaker/amazon';

class SecurityGroupPickerController implements ng.IComponentController {
  public securityGroups: any;
  public availableGroups: ISecurityGroup[];
  public credentials: IAggregatedAccounts;
  public command: any;
  public groupsToEdit: string[];
  public removedGroups: string[];
  public availableSecurityGroups: any[];
  public accountChanged: Subject<void>;
  public regionChanged: Subject<void>;
  public groupsRemoved: Subject<string[]>;
  public hideLabel: boolean;
  public amazonAccount: string;
  public loaded = false;
  private vpcs: IVpc[];
  private subscriptions: Subscription[];
  public firewallsLabel: string;

  public constructor(
    private $q: ng.IQService,
    private securityGroupReader: any,
    private cacheInitializer: CacheInitializerService,
  ) {
    'ngInject';
  }

  public $onInit(): void {
    const credentialLoader: ng.IPromise<void> = AccountService.getCredentialsKeyedByAccount('titus').then(
      (credentials: IAggregatedAccounts) => {
        this.credentials = credentials;
      },
    );
    const groupLoader: ng.IPromise<void> = this.securityGroupReader.getAllSecurityGroups().then((groups: any[]) => {
      this.securityGroups = groups;
    });
    const vpcLoader = VpcReader.listVpcs().then((vpcs: IVpc[]) => (this.vpcs = vpcs));
    this.$q.all([credentialLoader, groupLoader, vpcLoader]).then(() => this.configureSecurityGroupOptions());
    this.subscriptions = [
      this.accountChanged.subscribe(() => this.configureSecurityGroupOptions()),
      this.regionChanged.subscribe(() => this.configureSecurityGroupOptions()),
    ];

    this.firewallsLabel = FirewallLabels.get('firewalls');
  }

  public $onDestroy(): void {
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  private getCredentials(): IAccountDetails {
    return this.credentials[this.command.credentials];
  }

  private getAwsAccount(): string {
    return this.getCredentials().awsAccount;
  }

  private getRegion(): string {
    return this.command.region || (this.command.cluster ? this.command.cluster.region : null);
  }

  private getVpcId(): string {
    const credentials = this.getCredentials();
    const match = this.vpcs.find(
      vpc =>
        vpc.name === credentials.awsVpc &&
        vpc.account === credentials.awsAccount &&
        vpc.region === this.getRegion() &&
        vpc.cloudProvider === 'aws',
    );
    return match ? match.id : null;
  }

  private getRegionalSecurityGroups(): ISecurityGroup[] {
    const newSecurityGroups: any = this.securityGroups[this.getAwsAccount()] || { aws: {} };
    return _.chain<ISecurityGroup>(newSecurityGroups.aws[this.getRegion()])
      .filter({ vpcId: this.getVpcId() })
      .sortBy('name')
      .value();
  }

  public refreshSecurityGroups(skipCommandReconfiguration: boolean): ng.IPromise<any[]> {
    return this.cacheInitializer.refreshCache('securityGroups').then(() => {
      return this.securityGroupReader.getAllSecurityGroups().then((securityGroups: any[]) => {
        this.securityGroups = securityGroups;
        if (!skipCommandReconfiguration) {
          this.configureSecurityGroupOptions();
        }
      });
    });
  }

  private configureSecurityGroupOptions(): void {
    const currentOptions = this.availableGroups;
    const newRegionalSecurityGroups = this.getRegionalSecurityGroups();
    if (currentOptions && this.groupsToEdit) {
      // not initializing - we are actually changing groups
      const currentGroupNames: string[] = this.groupsToEdit.map((groupId: string) => {
        const match = currentOptions.find(o => o.id === groupId);
        return match ? match.name : groupId;
      });

      const matchedGroups: ISecurityGroup[] = this.groupsToEdit
        .map((groupId: string) => {
          const securityGroup: any = currentOptions.find(o => o.id === groupId || o.name === groupId);
          return securityGroup ? securityGroup.name : null;
        })
        .map((groupName: string) => newRegionalSecurityGroups.find(g => g.name === groupName))
        .filter((group: any) => group);

      const matchedGroupNames: string[] = matchedGroups.map(g => g.name);
      const removed: string[] = _.xor(currentGroupNames, matchedGroupNames);
      this.groupsToEdit = matchedGroups.map(g => g.id);
      if (removed.length) {
        this.removedGroups.length = 0;
        this.removedGroups.push(...removed);
        this.groupsRemoved.next(removed);
      }
    }
    this.availableGroups = newRegionalSecurityGroups.sort((a, b) => {
      if (this.groupsToEdit.includes(a.id)) {
        return -1;
      }
      if (this.groupsToEdit.includes(b.id)) {
        return 1;
      }
      return a.name.localeCompare(b.name);
    });
    this.loaded = true;
  }
}

class SecurityGroupPickerComponent implements ng.IComponentOptions {
  public bindings: any = {
    command: '<',
    accountChanged: '<',
    regionChanged: '<',
    groupsRemoved: '<',
    removedGroups: '<',
    groupsToEdit: '=',
    hideLabel: '<',
    amazonAccount: '<',
  };
  public controller: any = SecurityGroupPickerController;
  public template = `
    <div class="clearfix" ng-if="$ctrl.loaded">
      <server-group-security-groups-removed removed="$ctrl.removedGroups"></server-group-security-groups-removed>
      <server-group-security-group-selector command="$ctrl.command" hide-label="$ctrl.hideLabel"
          groups-to-edit="$ctrl.groupsToEdit"
          refresh="$ctrl.refreshSecurityGroups()"
          help-key="titus.deploy.securityGroups"
          available-groups="$ctrl.availableGroups"></server-group-security-group-selector>

      <div class="small" ng-class="{ 'col-md-9': !$ctrl.hideLabel, 'col-md-offset-3': !$ctrl.hideLabel }" ng-if="$ctrl.amazonAccount && $ctrl.command.credentials !== undefined">
        Uses {{$ctrl.firewallsLabel}} from the Amazon account <account-tag account="$ctrl.amazonAccount" pad="right"></account-tag>
      </div>
    </div>
`;
}

export const TITUS_SECURITY_GROUP_PICKER = 'spinnaker.titus.securityGroup.picker.component';
module(TITUS_SECURITY_GROUP_PICKER, [SECURITY_GROUP_READER, CACHE_INITIALIZER_SERVICE]).component(
  'titusSecurityGroupPicker',
  new SecurityGroupPickerComponent(),
);
