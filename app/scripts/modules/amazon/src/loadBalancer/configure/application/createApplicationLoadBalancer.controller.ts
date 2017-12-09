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

import { IALBListener,
  IALBListenerCertificate,
  IAmazonApplicationLoadBalancer,
  IAmazonApplicationLoadBalancerUpsertCommand,
  IAmazonLoadBalancer,
  IListenerRule
} from 'amazon/domain';
import { AMAZON_CERTIFICATE_READ_SERVICE, AmazonCertificateReader } from 'amazon/certificates/amazon.certificate.read.service';
import { AWS_LOAD_BALANCER_TRANSFORMER, AwsLoadBalancerTransformer } from 'amazon/loadBalancer/loadBalancer.transformer';
import { CreateAmazonLoadBalancerCtrl, ICreateAmazonLoadBalancerViewState } from '../common/createAmazonLoadBalancer.controller';
import { SUBNET_SELECT_FIELD_COMPONENT } from 'amazon/subnet/subnetSelectField.component';

class CreateApplicationLoadBalancerCtrl extends CreateAmazonLoadBalancerCtrl {
  protected pages = {
    location: require('../common/createLoadBalancerLocation.html'),
    securityGroups: require('../common/securityGroups.html'),
    listeners: require('./listeners.html'),
    targetGroups: require('./targetGroups.html'),
  };

  protected ruleSortOptions = {
    axis: 'y',
    delay: 150,
    handle: '.glyphicon-resize-vertical',
  };

  public existingLoadBalancerNames: string[];
  public existingTargetGroupNames: string[];
  public viewState: ICreateAmazonLoadBalancerViewState;
  public loadBalancerCommand: IAmazonApplicationLoadBalancerUpsertCommand;

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
              protected loadBalancer: IAmazonApplicationLoadBalancer,
              protected isNew: boolean,
              protected forPipelineConfig: boolean) {
    'ngInject';
    super($scope, $uibModalInstance, $state, accountService, securityGroupReader, amazonCertificateReader, cacheInitializer, infrastructureCaches, v2modalWizardService, loadBalancerWriter, taskMonitorBuilder, subnetReader, namingService, application, isNew, forPipelineConfig);
  }

  protected initializeController(): void {
    if (this.loadBalancer) {
      this.loadBalancerCommand = this.awsLoadBalancerTransformer.convertApplicationLoadBalancerForEditing(this.loadBalancer);
      if (this.forPipelineConfig) {
        this.initializeCreateMode();
      } else {
        this.initializeEditMode();
      }
      if (this.isNew) {
        this.buildName();
      }
    } else {
      this.loadBalancerCommand = this.awsLoadBalancerTransformer.constructNewApplicationLoadBalancerTemplate(this.application);
    }
    if (this.isNew) {
      this.initializeCreateMode();
    }
    this.updateLoadBalancerNames();
  }

  protected updateLoadBalancerNames(): void {
    const account = this.loadBalancerCommand.credentials,
          region = this.loadBalancerCommand.region;

    const accountLoadBalancersByRegion: { [region: string]: string[] } = {};
    const accountTargetGroupsByRegion: { [region: string]: string[] } = {};
    this.application.getDataSource('loadBalancers').refresh(true).then(() => {
      this.application.getDataSource('loadBalancers').data.forEach((loadBalancer: IAmazonLoadBalancer) => {
        if (loadBalancer.account === account) {
          accountLoadBalancersByRegion[loadBalancer.region] = accountLoadBalancersByRegion[loadBalancer.region] || [];
          accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);

          if (loadBalancer.loadBalancerType === 'application') {
            const lb = loadBalancer as IAmazonApplicationLoadBalancer;
            if (!this.loadBalancer || lb.name !== this.loadBalancer.name) {
              lb.targetGroups.forEach((targetGroup) => {
                accountTargetGroupsByRegion[loadBalancer.region] = accountTargetGroupsByRegion[loadBalancer.region] ||  [];
                accountTargetGroupsByRegion[loadBalancer.region].push(this.modifyTargetGroupName(targetGroup.name, false));
              });
            }
          }
        }
      });

      this.existingLoadBalancerNames = accountLoadBalancersByRegion[region] || [];
      this.existingTargetGroupNames = accountTargetGroupsByRegion[region] || [];
    });
  }

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
      ],
      rules: [],
    });
  }

  public addRule(listener: IALBListener): void {
    const newRule: IListenerRule = {
      priority: null,
      actions: [{
        type: 'forward',
        targetGroupName: undefined
      }],
      conditions: [{
        field: 'path-pattern',
        values: ['']
      }],
    };

    listener.rules.push(newRule);
  }

  public removeRule(listener: IALBListener, index: number): void {
    listener.rules.splice(index, 1);
  }

  public addCondition(rule: IListenerRule): void {
    if (rule.conditions.length === 1) {
      const field = rule.conditions[0].field === 'path-pattern' ? 'host-header' : 'path-pattern';
      rule.conditions.push({ field, values: [''] });
    }
  }

  public removeCondition(rule: IListenerRule, index: number) {
    rule.conditions.splice(index, 1);
  }

  public listenerProtocolChanged(listener: IALBListener): void {
    if (listener.protocol === 'HTTPS') {
      listener.port = 443;
      if (!listener.certificates || listener.certificates.length === 0) {
        this.addListenerCertificate(listener);
      }
    }
    if (listener.protocol === 'HTTP') {
      listener.port = 80;
    }
  }

  public addListenerCertificate(listener: IALBListener): void {
    listener.certificates = listener.certificates || [];
    listener.certificates.push({
      certificateArn: undefined,
      type: 'iam',
      name: undefined,
    });
  }

  public removeListenerCertificate(listener: IALBListener, index: number): void {
    if (listener && listener.certificates && listener.certificates.length > index) {
      listener.certificates.splice(index, 1);
    }
  }

  public showSslCertificateNameField(): boolean {
    return this.loadBalancerCommand.listeners.some((listener) => listener.protocol === 'HTTPS');
  }

  public showCertificateSelect(certificate: IALBListenerCertificate): boolean {
    return certificate.type === 'iam' && this.certificates && Object.keys(this.certificates).length > 0;
  }

  public removeTargetGroup(index: number): void {
    this.loadBalancerCommand.targetGroups.splice(index, 1);
  }

  public addTargetGroup(): void {
    const tgLength = this.loadBalancerCommand.targetGroups.length;
    this.loadBalancerCommand.targetGroups.push({
      name: `targetgroup${tgLength ? `${tgLength}` : ''}`,
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
  }

  private modifyTargetGroupName(name: string, add: boolean): string {
    if (add) {
      return `${this.application.name}-${name}`;
    }
    return name.replace(`${this.application.name}-`, '');
  }

  private manageTargetGroupNames(command: IAmazonApplicationLoadBalancerUpsertCommand, add: boolean): void {
    (command.targetGroups || []).forEach((targetGroupDescription) => {
      targetGroupDescription.name = this.modifyTargetGroupName(targetGroupDescription.name, add);
    });
    (command.listeners || []).forEach((listenerDescription) => {
      listenerDescription.defaultActions.forEach((actionDescription) => {
        actionDescription.targetGroupName = this.modifyTargetGroupName(actionDescription.targetGroupName, add);
      });
      (listenerDescription.rules || []).forEach((ruleDescription) => {
        ruleDescription.actions.forEach((actionDescription) => {
          actionDescription.targetGroupName = this.modifyTargetGroupName(actionDescription.targetGroupName, add);
        });
      });
    });
  }

  protected formatListeners(command: IAmazonApplicationLoadBalancerUpsertCommand): IPromise<void> {
    return this.accountService.getAccountDetails(command.credentials).then((account) => {
      command.listeners.forEach((listener) => {
        listener.certificates.forEach((certificate) => {
          certificate.certificateArn = this.certificateIdAsARN(account.accountId, certificate.name,
          command.region, certificate.type || this.certificateTypes[0]);
        });
      });
    });
  }

  private manageRules(command: IAmazonApplicationLoadBalancerUpsertCommand): void {
    command.listeners.forEach((listener) => {
      listener.rules.forEach((rule, index) => {
        // Set the priority in array order, starting with 1
        rule.priority = index + 1;
        // Remove conditions that have no value
        rule.conditions = rule.conditions.filter((condition) => condition.values[0].length > 0);
      });
    });
  }

  protected formatCommand(command: IAmazonApplicationLoadBalancerUpsertCommand): void {
    this.setAvailabilityZones(command);
    this.manageTargetGroupNames(command, true);
    this.manageRules(command);
  }
}

export const AWS_CREATE_APPLICATION_LOAD_BALANCER_CTRL = 'spinnaker.amazon.loadBalancer.application.create.controller';
module(AWS_CREATE_APPLICATION_LOAD_BALANCER_CTRL, [
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
  require('../common/loadBalancerAvailabilityZoneSelector.directive.js').name,
  SUBNET_SELECT_FIELD_COMPONENT,
]).controller('awsCreateApplicationLoadBalancerCtrl', CreateApplicationLoadBalancerCtrl);
