import {module} from 'angular';
import * as _ from 'lodash';
import {Subject, Subscription} from 'rxjs';

import {ISecurityGroup, IVpc} from 'core/domain';
import {
  ACCOUNT_SERVICE, AccountService, IAggregatedAccounts,
  IAccountDetails
} from 'core/account/account.service';
import {CACHE_INITIALIZER_SERVICE, CacheInitializerService} from 'core/cache/cacheInitializer.service';

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
  public loaded: boolean = false;
  private vpcs: IVpc[];
  private subscriptions: Subscription[];

  static get $inject(): string[] { return ['$q', 'securityGroupReader', 'accountService', 'cacheInitializer', 'vpcReader']; }

  public constructor(private $q: ng.IQService,
                     private securityGroupReader: any,
                     private accountService: AccountService,
                     private cacheInitializer: CacheInitializerService,
                     private vpcReader: any) {}

  public $onInit(): void {
    let credentialLoader: ng.IPromise<void> = this.accountService.getCredentialsKeyedByAccount('titus').then((credentials: IAggregatedAccounts) => {
      this.credentials = credentials;
    });
    let groupLoader: ng.IPromise<void> = this.securityGroupReader.getAllSecurityGroups().then((groups: any[]) => {
      this.securityGroups = groups;
    });
    let vpcLoader: ng.IPromise<void> = this.vpcReader.listVpcs().then((vpcs: IVpc[]) => this.vpcs = vpcs);
    this.$q.all([credentialLoader, groupLoader, vpcLoader]).then(() => this.configureSecurityGroupOptions());
    this.subscriptions = [
      this.accountChanged.subscribe(() => this.configureSecurityGroupOptions()),
      this.regionChanged.subscribe(() => this.configureSecurityGroupOptions())
    ];
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
    let credentials = this.getCredentials();
    let match = this.vpcs.find(vpc =>
      vpc.name === credentials.awsVpc
      && vpc.account === credentials.awsAccount
      && vpc.region === this.getRegion()
      && vpc.cloudProvider === 'aws'
    );
    return match ? match.id : null;
  }

  private getRegionalSecurityGroups(): ISecurityGroup[] {
    let newSecurityGroups: any = this.securityGroups[this.getAwsAccount()] || { aws: {}};
    return _.chain<ISecurityGroup>(newSecurityGroups.aws[this.getRegion()])
      .filter({vpcId: this.getVpcId()})
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
    let currentOptions = this.availableGroups;
    let newRegionalSecurityGroups = this.getRegionalSecurityGroups();
    if (currentOptions && this.groupsToEdit) {
      // not initializing - we are actually changing groups
      let currentGroupNames: string[] = this.groupsToEdit.map((groupId: string) => {
        let match = currentOptions.find(o => o.id === groupId);
        return match ? match.name : groupId;
      });

      let matchedGroups: ISecurityGroup[] = this.groupsToEdit.map((groupId: string) => {
        let securityGroup: any = currentOptions.find(o => o.id === groupId || o.name === groupId);
        return securityGroup ? securityGroup.name : null;
      }).map((groupName: string) => newRegionalSecurityGroups.find(g => g.name === groupName)).filter((group: any) => group);

      let matchedGroupNames: string[] = matchedGroups.map(g => g.name);
      let removed: string[] = _.xor(currentGroupNames, matchedGroupNames);
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
  };
  public controller: any = SecurityGroupPickerController;
  public template: string = `
    <div class="clearfix" ng-if="$ctrl.loaded">
      <server-group-security-groups-removed removed="$ctrl.removedGroups"></server-group-security-groups-removed>
      <server-group-security-group-selector command="$ctrl.command" hide-label="$ctrl.hideLabel" 
          groups-to-edit="$ctrl.groupsToEdit"
          refresh="$ctrl.refreshSecurityGroups()" 
          help-key="titus.deploy.securityGroups"
          available-groups="$ctrl.availableGroups"></server-group-security-group-selector>
    </div>
`;
}

const moduleName = 'spinnaker.titus.securityGroup.picker.component';
module(moduleName, [
  ACCOUNT_SERVICE,
  require('core/securityGroup/securityGroup.read.service'),
  CACHE_INITIALIZER_SERVICE,
  require('amazon/vpc/vpc.read.service'),
])
  .component('titusSecurityGroupPicker', new SecurityGroupPickerComponent());

export default moduleName;
