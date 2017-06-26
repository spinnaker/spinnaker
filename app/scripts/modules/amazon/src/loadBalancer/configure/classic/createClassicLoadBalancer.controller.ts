import { module, IScope, IPromise } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { StateService } from '@uirouter/angularjs';

import {
  ACCOUNT_SERVICE,
  AccountService,
  Application,
  CACHE_INITIALIZER_SERVICE,
  CacheInitializerService,
  INFRASTRUCTURE_CACHE_SERVICE,
  InfrastructureCacheService,
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
  V2_MODAL_WIZARD_SERVICE
} from '@spinnaker/core';

import { AMAZON_CERTIFICATE_READ_SERVICE, AmazonCertificateReader } from 'amazon/certificates/amazon.certificate.read.service';
import { AWS_LOAD_BALANCER_TRANSFORMER, AwsLoadBalancerTransformer } from 'amazon/loadBalancer/loadBalancer.transformer';
import { IAmazonClassicLoadBalancer, IAmazonClassicLoadBalancerUpsertCommand, IClassicListenerDescription } from 'amazon/domain';
import { CreateAmazonLoadBalancerCtrl } from '../common/createAmazonLoadBalancer.controller';
import { SUBNET_SELECT_FIELD_COMPONENT } from 'amazon/subnet/subnetSelectField.component';

export class CreateClassicLoadBalancerCtrl extends CreateAmazonLoadBalancerCtrl {
  protected pages = {
    location: require('../common/createLoadBalancerLocation.html'),
    securityGroups: require('../common/securityGroups.html'),
    listeners: require('./listeners.html'),
    healthCheck: require('./healthCheck.html'),
    advancedSettings: require('./advancedSettings.html'),
  };

  public loadBalancerCommand: IAmazonClassicLoadBalancerUpsertCommand;

  constructor($scope: IScope,
              $uibModalInstance: IModalInstanceService,
              $state: StateService,
              protected accountService: AccountService,
              protected awsLoadBalancerTransformer: AwsLoadBalancerTransformer,
              securityGroupReader: SecurityGroupReader,
              amazonCertificateReader: AmazonCertificateReader,
              cacheInitializer: CacheInitializerService,
              infrastructureCaches: InfrastructureCacheService,
              v2modalWizardService: any,
              loadBalancerWriter: LoadBalancerWriter,
              taskMonitorBuilder: TaskMonitorBuilder,
              subnetReader: SubnetReader,
              namingService: NamingService,
              protected application: Application,
              protected loadBalancer: IAmazonClassicLoadBalancer,
              protected isNew: boolean,
              protected forPipelineConfig: boolean) {
    'ngInject';
    super($scope, $uibModalInstance, $state, accountService, securityGroupReader, amazonCertificateReader, cacheInitializer, infrastructureCaches, v2modalWizardService, loadBalancerWriter, taskMonitorBuilder, subnetReader, namingService, application, isNew, forPipelineConfig);
  }

  protected initializeController(): void {
    if (this.loadBalancer) {
      this.loadBalancerCommand = this.awsLoadBalancerTransformer.convertClassicLoadBalancerForEditing(this.loadBalancer);
      if (this.forPipelineConfig) {
        this.initializeCreateMode();
      } else {
        this.initializeEditMode();
      }
      if (this.isNew) {
        this.buildName();
      }
    } else {
      this.loadBalancerCommand = this.awsLoadBalancerTransformer.constructNewClassicLoadBalancerTemplate(this.application);
    }
    if (this.isNew) {
      this.updateLoadBalancerNames();
      this.initializeCreateMode();
    }
  }

  protected formatListeners(command: IAmazonClassicLoadBalancerUpsertCommand): IPromise<void> {
    return this.accountService.getAccountDetails(command.credentials).then((account) => {
      command.listeners.forEach((listener) => {
        listener.sslCertificateId = this.certificateIdAsARN(account.accountId, listener.sslCertificateName,
          command.region, listener.sslCertificateType || this.certificateTypes[0]);
      });
    });
  }

  public requiresHealthCheckPath(): boolean {
    return this.loadBalancerCommand.healthCheckProtocol && this.loadBalancerCommand.healthCheckProtocol.indexOf('HTTP') === 0;
  }

  public prependForwardSlash(text: string): string {
    return text && text.indexOf('/') !== 0 ? `/${text}` : text;
  }

  public addListener(): void {
    this.loadBalancerCommand.listeners.push({internalProtocol: 'HTTP', externalProtocol: 'HTTP', externalPort: 80, internalPort: undefined});
  }

  public listenerProtocolChanged(listener: IClassicListenerDescription): void {
    if (listener.externalProtocol === 'HTTPS') {
      listener.externalPort = 443;
    }
    if (listener.externalProtocol === 'HTTP') {
      listener.externalPort = 80;
    }
  }

  public showSslCertificateNameField(): boolean {
    return this.loadBalancerCommand.listeners.some((listener) => {
      return listener.externalProtocol === 'HTTPS' || listener.externalProtocol === 'SSL';
    });
  }

  public showCertificateSelect(listener: IClassicListenerDescription): boolean {
    return listener.sslCertificateType === 'iam' &&
      (listener.externalProtocol === 'HTTPS' || listener.externalProtocol === 'SSL') &&
      this.certificates && Object.keys(this.certificates).length > 0;
  }

  protected formatCommand(command: IAmazonClassicLoadBalancerUpsertCommand): void {
    this.setAvailabilityZones(command);
    this.clearSecurityGroupsIfNotInVpc(command);
    this.addHealthCheckToCommand(command);
  }

  private clearSecurityGroupsIfNotInVpc(loadBalancer: IAmazonClassicLoadBalancerUpsertCommand): void {
    if (!loadBalancer.vpcId && !loadBalancer.subnetType) {
      loadBalancer.securityGroups = null;
    }
  }

  private addHealthCheckToCommand(loadBalancer: IAmazonClassicLoadBalancerUpsertCommand): void {
    let healthCheck = null;
    const protocol = loadBalancer.healthCheckProtocol || '';
    if (protocol.startsWith('HTTP')) {
      healthCheck = `${protocol}:${loadBalancer.healthCheckPort}${loadBalancer.healthCheckPath}`;
    } else {
      healthCheck = `${protocol}:${loadBalancer.healthCheckPort}`;
    }
    loadBalancer.healthCheck = healthCheck;
  }
}

export const AWS_CREATE_CLASSIC_LOAD_BALANCER_CTRL = 'spinnaker.amazon.loadBalancer.classic.create.controller';
module(AWS_CREATE_CLASSIC_LOAD_BALANCER_CTRL, [
  require('@uirouter/angularjs').default,
  LOAD_BALANCER_WRITE_SERVICE,
  ACCOUNT_SERVICE,
  AWS_LOAD_BALANCER_TRANSFORMER,
  SECURITY_GROUP_READER,
  AMAZON_CERTIFICATE_READ_SERVICE,
  V2_MODAL_WIZARD_SERVICE,
  TASK_MONITOR_BUILDER,
  SUBNET_READ_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  NAMING_SERVICE,
  require('../common/loadBalancerAvailabilityZoneSelector.directive.js'),
  SUBNET_SELECT_FIELD_COMPONENT,
]).controller('awsCreateClassicLoadBalancerCtrl', CreateClassicLoadBalancerCtrl);
