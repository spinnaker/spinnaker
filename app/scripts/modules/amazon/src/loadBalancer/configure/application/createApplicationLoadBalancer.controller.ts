import { module, IScope, IPromise } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { StateService } from '@uirouter/angularjs';
import { chain, clone, find, filter, map, trimEnd, uniq, values } from 'lodash';

import {
  ACCOUNT_SERVICE,
  AccountService,
  Application,
  CACHE_INITIALIZER_SERVICE,
  CacheInitializerService,
  IAccount,
  IGroupsByAccount,
  INFRASTRUCTURE_CACHE_SERVICE,
  InfrastructureCacheService,
  IRegion,
  IRegionAccount,
  ISubnet,
  LOAD_BALANCER_WRITE_SERVICE,
  LoadBalancerWriter,
  NAMING_SERVICE,
  NamingService,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  SUBNET_READ_SERVICE,
  SubnetReader,
  TASK_MONITOR_BUILDER,
  TaskMonitorBuilder,
  TaskMonitor,
  V2_MODAL_WIZARD_SERVICE
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { IALBListener,
  IAmazonApplicationLoadBalancer,
  IAmazonApplicationLoadBalancerUpsertCommand
} from 'amazon/domain';
import { AWS_LOAD_BALANCER_TRANFORMER, AwsLoadBalancerTransformer } from 'amazon/loadBalancer/loadBalancer.transformer';
import { SUBNET_SELECT_FIELD_COMPONENT } from 'amazon/subnet/subnetSelectField.component';

import './configure.less';

export interface ICreateApplicationLoadBalancerViewState {
  accountsLoaded: boolean;
  currentItems: number;
  hideInternalFlag: boolean;
  internalFlagToggled: boolean;
  refreshingSecurityGroups: boolean;
  removedSecurityGroups: string[];
  securityGroupRefreshTime: number;
  securityGroupsLoaded: boolean;
  submitButtonLabel: string;
  submitting: boolean;
}

export interface ISubnetOption {
  purpose: string;
  label: string;
  deprecated: boolean;
  vpcIds: string[];
  availabilityZones: string[];
}

class CreateApplicationLoadBalancerCtrl {
  public pages = {
    location: require('../common/createLoadBalancerLocation.html'),
    securityGroups: require('../common/securityGroups.html'),
    listeners: require('./listeners.html'),
    targetGroups: require('./targetGroups.html'),
  };

  public existingLoadBalancerNames: string[];
  public viewState: ICreateApplicationLoadBalancerViewState;
  private accounts: IAccount[];
  private allSecurityGroups: IGroupsByAccount;
  private availableSecurityGroups: IRegionAccount[];
  private availabilityZones: string[];
  private certificateTypes: string[];
  private defaultSecurityGroups: string[];
  private existingSecurityGroupNames: string[];
  private loadBalancerCommand: IAmazonApplicationLoadBalancerUpsertCommand;
  private regions: IRegion[];
  private subnets: ISubnetOption[];
  private taskMonitor: TaskMonitor;

  constructor(private $scope: IScope,
              private $uibModalInstance: IModalInstanceService,
              private $state: StateService,
              private accountService: AccountService,
              private awsLoadBalancerTransformer: AwsLoadBalancerTransformer,
              private securityGroupReader: SecurityGroupReader,
              private cacheInitializer: CacheInitializerService,
              private infrastructureCaches: InfrastructureCacheService,
              private v2modalWizardService: any,
              private loadBalancerWriter: LoadBalancerWriter,
              private taskMonitorBuilder: TaskMonitorBuilder,
              private subnetReader: SubnetReader,
              private namingService: NamingService,
              private application: Application,
              private loadBalancer: IAmazonApplicationLoadBalancer,
              private isNew: boolean,
              private forPipelineConfig: boolean) {
    'ngInject';
    // if this controller is used in the context of "Create Load Balancer" stage,
    // then forPipelineConfig flag will be true. In that case, the Load Balancer
    // modal dialog will just return the Load Balancer object.

    this.viewState = {
      accountsLoaded: false,
      currentItems: 25,
      hideInternalFlag: false,
      internalFlagToggled: false,
      refreshingSecurityGroups: false,
      removedSecurityGroups: [],
      securityGroupRefreshTime: this.infrastructureCaches.get('securityGroups').getStats().ageMax,
      securityGroupsLoaded: false,
      submitButtonLabel: this.forPipelineConfig ? (isNew ? 'Add' : 'Done') : (isNew ? 'Create' : 'Update'),
      submitting: false,
    };

    this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: `${isNew ? 'Creating' : 'Updating'} your load balancer`,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => this.onTaskComplete()
    });

    this.certificateTypes = AWSProviderSettings.loadBalancers && AWSProviderSettings.loadBalancers.certificateTypes || ['iam', 'acm'];

    this.initializeController();
  }

  private initializeController(): void {
    this.setSecurityGroupRefreshTime();
    if (this.loadBalancer) {
      this.loadBalancerCommand = this.awsLoadBalancerTransformer.convertApplicationLoadBalancerForEditing(this.loadBalancer);
      if (this.forPipelineConfig) {
        this.initializeCreateMode();
      } else {
        this.initializeEditMode();
      }
      if (this.isNew) {
        const nameParts = this.namingService.parseLoadBalancerName(this.loadBalancerCommand.name);
        this.loadBalancerCommand.stack = nameParts.stack;
        this.loadBalancerCommand.detail = nameParts.freeFormDetails;
        delete this.loadBalancerCommand.name;
      }
    } else {
      this.loadBalancerCommand = this.awsLoadBalancerTransformer.constructNewApplicationLoadBalancerTemplate(this.application);
    }
    if (this.isNew) {
      this.updateLoadBalancerNames();
      this.initializeCreateMode();
    }
  }

  private onApplicationRefresh(): void {
    // If the user has already closed the modal, do not navigate to the new details view
    if (this.$scope.$$destroyed) {
      return;
    }
    this.$uibModalInstance.close();
    const newStateParams = {
      name: this.loadBalancerCommand.name,
      accountId: this.loadBalancerCommand.credentials,
      region: this.loadBalancerCommand.region,
      vpcId: this.loadBalancerCommand.vpcId,
      provider: 'aws',
    };

    if (!this.$state.includes('**.loadBalancerDetails')) {
      this.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      this.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }

  private onTaskComplete(): void {
    this.application.loadBalancers.refresh();
    this.application.loadBalancers.onNextRefresh(this.$scope, () => this.onApplicationRefresh());
  }

  private initializeEditMode(): void {
    if (this.loadBalancerCommand.vpcId) {
      this.preloadSecurityGroups().then(() => {
        this.updateAvailableSecurityGroups([this.loadBalancerCommand.vpcId]);
      });
    }
  }

  private initializeCreateMode(): void {
    this.preloadSecurityGroups();
    if (AWSProviderSettings) {
      if (AWSProviderSettings.defaultSecurityGroups) {
        this.defaultSecurityGroups = AWSProviderSettings.defaultSecurityGroups;
      }
      if (AWSProviderSettings.loadBalancers && AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet) {
        delete this.loadBalancerCommand.isInternal;
        this.viewState.hideInternalFlag = true;
      }
    }
    this.accountService.listAccounts('aws').then((accounts) => {
      this.accounts = accounts;
      this.viewState.accountsLoaded = true;
      this.accountUpdated();
    });
  }

  private preloadSecurityGroups(): IPromise<void> {
    return this.securityGroupReader.getAllSecurityGroups().then((securityGroups) => {
      this.allSecurityGroups = securityGroups;
      this.viewState.securityGroupsLoaded = true;
    });
  }

  private availableGroupsSorter(a: IRegionAccount, b: IRegionAccount): number {
    if (this.defaultSecurityGroups) {
      if (this.defaultSecurityGroups.includes(a.name)) {
        return -1;
      }
      if (this.defaultSecurityGroups.includes(b.name)) {
        return 1;
      }
    }
    return this.loadBalancerCommand.securityGroups.includes(a.id) ? -1 :
      this.loadBalancerCommand.securityGroups.includes(b.id) ? 1 :
        0;
  }

  private updateAvailableSecurityGroups(availableVpcIds: string[]): void {
    const account = this.loadBalancerCommand.credentials,
          region = this.loadBalancerCommand.region;

    if (account && region && this.allSecurityGroups[account] && this.allSecurityGroups[account].aws[region]) {
      this.availableSecurityGroups = filter(this.allSecurityGroups[account].aws[region], (securityGroup) => {
        return availableVpcIds.includes(securityGroup.vpcId);
      }).sort((a, b) => this.availableGroupsSorter(a, b)); // push existing groups to top
      this.existingSecurityGroupNames = map(this.availableSecurityGroups, 'name');
      const existingNames = this.defaultSecurityGroups.filter((name) => this.existingSecurityGroupNames.includes(name));
      this.loadBalancerCommand.securityGroups.forEach((securityGroup) => {
        if (!this.existingSecurityGroupNames.includes(securityGroup)) {
          const matches = filter(this.availableSecurityGroups, {id: securityGroup});
          if (matches.length) {
            existingNames.push(matches[0].name);
          } else {
            if (!this.defaultSecurityGroups.includes(securityGroup)) {
              this.viewState.removedSecurityGroups.push(securityGroup);
            }
          }
        } else {
          existingNames.push(securityGroup);
        }
      });
      this.loadBalancerCommand.securityGroups = uniq(existingNames);
      if (this.viewState.removedSecurityGroups.length) {
        this.v2modalWizardService.markDirty('Security Groups');
      }
    } else {
      this.clearSecurityGroups();
    }
  }

  private updateLoadBalancerNames(): void {
    const account = this.loadBalancerCommand.credentials,
          region = this.loadBalancerCommand.region;

    const accountLoadBalancersByRegion: { [region: string]: string[] } = {};
    this.application.getDataSource('loadBalancers').refresh(true).then(() => {
      this.application.getDataSource('loadBalancers').data.forEach((loadBalancer) => {
        if (loadBalancer.account === account) {
          accountLoadBalancersByRegion[loadBalancer.region] = accountLoadBalancersByRegion[loadBalancer.region] || [];
          accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);
        }
      });

      this.existingLoadBalancerNames = accountLoadBalancersByRegion[region] || [];
    });
  }

  private getAvailableSubnets(): IPromise<ISubnet[]> {
    const account = this.loadBalancerCommand.credentials,
          region = this.loadBalancerCommand.region;
    return this.subnetReader.listSubnets().then((subnets) => {
      return chain(subnets)
        .filter({account: account, region: region})
        .reject({target: 'ec2'})
        .value();
    });
  }

  private updateAvailabilityZones(): void {
    const selected = this.regions ? this.regions.filter((region) => region.name === this.loadBalancerCommand.region) : [];
    if (selected.length) {
      this.loadBalancerCommand.regionZones = clone(selected[0].availabilityZones);
      this.availabilityZones = selected[0].availabilityZones;
    } else {
      this.availabilityZones = [];
    }
  }

  private updateSubnets(): void {
    this.getAvailableSubnets().then((subnets) => {
      const subnetOptions = subnets.reduce((accumulator, subnet) => {
        if (!accumulator[subnet.purpose]) {
          accumulator[subnet.purpose] = { purpose: subnet.purpose, label: subnet.label, deprecated: subnet.deprecated, vpcIds: [], availabilityZones: [] };
        }
        const acc = accumulator[subnet.purpose];
        if (acc.vpcIds.indexOf(subnet.vpcId) === -1) {
          acc.vpcIds.push(subnet.vpcId);
        }
        acc.availabilityZones.push(subnet.availabilityZone);
        return accumulator;
      }, {} as { [purpose: string]: ISubnetOption });

      this.setSubnetTypeFromVpc(subnetOptions);

      if (!subnetOptions[this.loadBalancerCommand.subnetType]) {
        this.loadBalancerCommand.subnetType = '';
      }
      this.subnets = values(subnetOptions);
      this.subnetUpdated();
    });
  }

  private setSubnetTypeFromVpc(subnetOptions: { [purpose: string]: ISubnetOption }): void {
    if (this.loadBalancerCommand.vpcId) {
      const currentSelection = find(subnetOptions, (option) => option.vpcIds.includes(this.loadBalancerCommand.vpcId));
      if (currentSelection) {
        this.loadBalancerCommand.subnetType = currentSelection.purpose;
      }
      delete this.loadBalancerCommand.vpcId;
    }
  }

  private clearSecurityGroups(): void {
    this.availableSecurityGroups = [];
    this.existingSecurityGroupNames = [];
  }

  private certificateIdAsARN(accountId: string, certificateId: string, region: string, certificateType: string): string {
    if (certificateId && (certificateId.indexOf('arn:aws:iam::') !== 0 || certificateId.indexOf('arn:aws:acm:') !== 0)) {
      // If they really want to enter the ARN...
      if (certificateType === 'iam') {
        return `arn:aws:iam::${accountId}:server-certificate/${certificateId}`;
      }
      if (certificateType === 'acm') {
        return `arn:aws:acm:${region}:${accountId}:certificate/${certificateId}`;
      }
    }
    return certificateId;
  }

  public refreshSecurityGroups(): void {
    this.viewState.refreshingSecurityGroups = true;
    this.cacheInitializer.refreshCache('securityGroups').then(() => {
      this.viewState.refreshingSecurityGroups = false;
      this.setSecurityGroupRefreshTime();
      this.preloadSecurityGroups().then(() => {
        this.updateAvailableSecurityGroups([this.loadBalancerCommand.vpcId]);
      });
    });
  };

  private setSecurityGroupRefreshTime(): void {
    this.viewState.securityGroupRefreshTime = this.infrastructureCaches.get('securityGroups').getStats().ageMax;
  }

  public addItems(): void {
    this.viewState.currentItems += 25;
  }

  public resetCurrentItems(): void {
    this.viewState.currentItems = 25;
  }

  private updateName(): void {
    this.loadBalancerCommand.name = this.getName();
  };

  private getName(): string {
    const elb = this.loadBalancerCommand;
    const elbName = [this.application.name, (elb.stack || ''), (elb.detail || '')].join('-');
    return trimEnd(elbName, '-');
  };

  private accountUpdated(): void {
    this.accountService.getRegionsForAccount(this.loadBalancerCommand.credentials).then((regions) => {
      this.regions = regions;
      this.clearSecurityGroups();
      this.regionUpdated();
    });
  };

  private regionUpdated(): void {
    this.updateAvailabilityZones();
    this.updateLoadBalancerNames();
    this.updateSubnets();
    this.updateName();
  };

  private subnetUpdated(): void {
    const subnetPurpose = this.loadBalancerCommand.subnetType || null,
        subnet = this.subnets.find((test) => test.purpose === subnetPurpose),
        availableVpcIds = subnet ? subnet.vpcIds : [];
    this.updateAvailableSecurityGroups(availableVpcIds);
    if (subnetPurpose) {
      this.loadBalancerCommand.vpcId = availableVpcIds.length ? availableVpcIds[0] : null;
      if (!this.viewState.hideInternalFlag && !this.viewState.internalFlagToggled) {
        this.loadBalancerCommand.isInternal = subnetPurpose.includes('internal');
      }
      this.availabilityZones = this.subnets
        .find(o => o.purpose === this.loadBalancerCommand.subnetType)
        .availabilityZones
        .sort();
      this.v2modalWizardService.includePage('Security Groups');
    } else {
      this.updateAvailabilityZones();
      this.loadBalancerCommand.vpcId = null;
      this.v2modalWizardService.excludePage('Security Groups');
    }
  };

  public internalFlagChanged(): void {
    this.viewState.internalFlagToggled = true;
  };

  public removeListener(index: number): void {
    this.loadBalancerCommand.listeners.splice(index, 1);
  };

  public addListener(): void {
    this.loadBalancerCommand.listeners.push({
      certificates: [],
      protocol: 'HTTP',
      port: 80,
      defaultActions: [
        {
          type: 'forward',
          targetGroupName: null
        }
      ]
    });
  };

  public listenerProtocolChanged(listener: IALBListener): void {
    if (listener.protocol === 'HTTPS') {
      listener.port = 443;
    }
    if (listener.protocol === 'HTTP') {
      listener.port = 80;
    }
  };

  public showSslCertificateNameField(): boolean {
    return this.loadBalancerCommand.listeners.some((listener) => listener.protocol === 'HTTPS');
  };

  public removeTargetGroup(index: number): void {
    this.loadBalancerCommand.targetGroups.splice(index, 1);
  };

  public addTargetGroup(): void {
    const tgLength = this.loadBalancerCommand.targetGroups.length;
    this.loadBalancerCommand.targetGroups.push({
      name: `${this.application.name}-alb-targetGroup${tgLength ? `-${tgLength}` : ''}`,
      protocol: 'HTTP',
      port: 7001,
      healthCheckProtocol: 'HTTP',
      healthCheckPort: '7001',
      healthCheckPath: '/healthcheck',
      healthCheckTimeout: 5,
      healthCheckInterval: 10,
      healthyThreshold: 10,
      unhealthyThreshold: 2,
      attributes: {
        deregistrationDelay: 600,
        stickinessEnabled: false,
        stickinessType: 'lb_cookie',
        stickinessDuration: 8400
      }
    });
  };

  private formatListeners(): IPromise<void> {
    return this.accountService.getAccountDetails(this.loadBalancerCommand.credentials).then((account) => {
      this.loadBalancerCommand.listeners.forEach((listener) => {
        listener.certificates.forEach((certificate) => {
          certificate.certificateArn = this.certificateIdAsARN(account.accountId, certificate.name,
          this.loadBalancerCommand.region, certificate.type || this.certificateTypes[0]);
        });
      });
    });
  };

  public submit(): void {
    const descriptor = this.isNew ? 'Create' : 'Update';

    if (this.forPipelineConfig) {
      // don't submit to backend for creation. Just return the loadBalancerCommand object
      this.formatListeners().then(() => {
        this.$uibModalInstance.close(this.loadBalancerCommand);
      });
    } else {
      this.taskMonitor.submit(() => {
          return this.formatListeners().then(() => {
            this.setAvailabilityZones(this.loadBalancerCommand);
            return this.loadBalancerWriter.upsertLoadBalancer(this.loadBalancerCommand, this.application, descriptor);
          });
        }
      );
    }
  };

  private setAvailabilityZones(loadBalancerCommand: IAmazonApplicationLoadBalancerUpsertCommand): void {
    const availabilityZones: { [region: string]: string[] } = {};
    availabilityZones[loadBalancerCommand.region] = loadBalancerCommand.regionZones || [];
    loadBalancerCommand.availabilityZones = availabilityZones;
  };

  public cancel() {
    this.$uibModalInstance.dismiss();
  };
}

export const AWS_CREATE_APPLICATION_LOAD_BALANCER_CTRL = 'spinnaker.amazon.loadBalancer.application.create.controller';
module(AWS_CREATE_APPLICATION_LOAD_BALANCER_CTRL, [
  require('@uirouter/angularjs').default,
  LOAD_BALANCER_WRITE_SERVICE,
  ACCOUNT_SERVICE,
  AWS_LOAD_BALANCER_TRANFORMER,
  SECURITY_GROUP_READER,
  V2_MODAL_WIZARD_SERVICE,
  TASK_MONITOR_BUILDER,
  SUBNET_READ_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  NAMING_SERVICE,
  require('../common/loadBalancerAvailabilityZoneSelector.directive.js'),
  SUBNET_SELECT_FIELD_COMPONENT,
]).controller('awsCreateApplicationLoadBalancerCtrl', CreateApplicationLoadBalancerCtrl);
