import { IController, IPromise, IQService, IScope, module } from 'angular';
import { StateService } from '@uirouter/angularjs';
import { head, sortBy, uniq } from 'lodash';

import {
  Application,
  APPLICATION_READ_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  IApplicationSecurityGroup,
  ILoadBalancer,
  ISecurityGroup,
  ISubnet,
  LOAD_BALANCER_READ_SERVICE,
  LOAD_BALANCER_WRITE_SERVICE,
  LoadBalancerReader,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  SUBNET_READ_SERVICE,
  SubnetReader,
} from '@spinnaker/core';

import {
  IAmazonApplicationLoadBalancer,
  IAmazonLoadBalancer,
  IAmazonLoadBalancerSourceData,
  IApplicationLoadBalancerSourceData,
  IClassicLoadBalancerSourceData,
  ITargetGroup,
} from 'amazon';

import { LOAD_BALANCER_ACTIONS } from './loadBalancerActions.component';

export interface ILoadBalancerFromStateParams {
  accountId: string;
  region: string;
  name: string;
}

export class AwsLoadBalancerDetailsController implements IController {
  public application: Application;
  public elbProtocol: string;
  public listeners: { in: string; targets: ITargetGroup[] }[];
  public loadBalancerFromParams: ILoadBalancerFromStateParams;
  public loadBalancer: IAmazonLoadBalancer;
  public securityGroups: ISecurityGroup[];
  public ipAddressTypeDescription: string;
  public state = { loading: true };

  constructor(
    private $scope: IScope,
    private $state: StateService,
    private $q: IQService,
    loadBalancer: ILoadBalancerFromStateParams,
    private app: Application,
    private securityGroupReader: SecurityGroupReader,
    private loadBalancerReader: LoadBalancerReader,
    private subnetReader: SubnetReader,
  ) {
    'ngInject';
    this.application = app;
    this.loadBalancerFromParams = loadBalancer;

    this.app
      .ready()
      .then(() => this.extractLoadBalancer())
      .then(() => {
        // If the user navigates away from the view before the initial extractLoadBalancer call completes,
        // do not bother subscribing to the refresh
        if (!$scope.$$destroyed) {
          app.getDataSource('loadBalancers').onRefresh($scope, () => this.extractLoadBalancer());
        }
      });
  }

  public autoClose(): void {
    if (this.$scope.$$destroyed) {
      return;
    }
    this.$state.params.allowModalToStayOpen = true;
    this.$state.go('^', null, { location: 'replace' });
  }

  public extractLoadBalancer(): IPromise<void> {
    const appLoadBalancer = this.app.loadBalancers.data.find((test: ILoadBalancer) => {
      return (
        test.name === this.loadBalancerFromParams.name &&
        test.region === this.loadBalancerFromParams.region &&
        test.account === this.loadBalancerFromParams.accountId
      );
    });

    if (appLoadBalancer) {
      const detailsLoader = this.loadBalancerReader.getLoadBalancerDetails(
        'aws',
        this.loadBalancerFromParams.accountId,
        this.loadBalancerFromParams.region,
        this.loadBalancerFromParams.name,
      );
      return detailsLoader.then(
        (details: IAmazonLoadBalancerSourceData[]) => {
          this.loadBalancer = appLoadBalancer;
          this.state.loading = false;
          const securityGroups: IApplicationSecurityGroup[] = [];
          if (details.length) {
            this.loadBalancer.elb = details[0];
            this.loadBalancer.elb.vpcId = this.loadBalancer.elb.vpcId || this.loadBalancer.elb.vpcid;
            this.loadBalancer.account = this.loadBalancerFromParams.accountId;

            const sourceData = details[0] as IApplicationLoadBalancerSourceData;
            if (sourceData.loadBalancerType === 'application' || sourceData.loadBalancerType === 'network') {
              // Transform listener data
              const elb = details[0] as IApplicationLoadBalancerSourceData;
              if (elb.listeners && elb.listeners.length) {
                this.elbProtocol = 'http:';
                if (elb.listeners.some((l: any) => l.protocol === 'HTTPS')) {
                  this.elbProtocol = 'https:';
                }

                this.listeners = [];
                elb.listeners.forEach(listener => {
                  const inPort = `${listener.protocol}:${listener.port}`;
                  const targets = uniq(
                    listener.rules.map(rule => {
                      const name = rule.actions[0].targetGroupName;
                      // TODO: Support target groups outside of this application...
                      return (
                        (this.loadBalancer as IAmazonApplicationLoadBalancer).targetGroups.find(
                          tg => tg.name === name,
                        ) || ({ name } as ITargetGroup)
                      );
                    }),
                  );

                  this.listeners.push({ in: inPort, targets });
                });
              }

              if (elb.ipAddressType === 'dualstack') {
                this.ipAddressTypeDescription = 'IPv4 and IPv6';
              }
              if (elb.ipAddressType === 'ipv4') {
                this.ipAddressTypeDescription = 'IPv4';
              }
            } else {
              // Classic
              const elb = details[0] as IClassicLoadBalancerSourceData;
              if (elb.listenerDescriptions) {
                this.elbProtocol = 'http:';
                if (elb.listenerDescriptions.some((l: any) => l.listener.protocol === 'HTTPS')) {
                  this.elbProtocol = 'https:';
                }
              }
            }

            this.loadBalancer.elb.securityGroups.forEach((securityGroupId: string) => {
              const match = this.securityGroupReader.getApplicationSecurityGroup(
                this.app,
                this.loadBalancerFromParams.accountId,
                this.loadBalancerFromParams.region,
                securityGroupId,
              );
              if (match) {
                securityGroups.push(match);
              }
            });
            this.securityGroups = sortBy(securityGroups, 'name');

            if (this.loadBalancer.subnets) {
              this.loadBalancer.subnetDetails = this.loadBalancer.subnets.reduce(
                (subnetDetails: ISubnet[], subnetId: string) => {
                  this.subnetReader
                    .getSubnetByIdAndProvider(subnetId, this.loadBalancer.provider)
                    .then((subnetDetail: ISubnet) => {
                      subnetDetails.push(subnetDetail);
                    });

                  return subnetDetails;
                },
                [],
              );
            }
          }
        },
        () => this.autoClose(),
      );
    } else {
      this.autoClose();
    }
    if (!this.loadBalancer) {
      this.autoClose();
    }

    return this.$q.when(null);
  }

  public getFirstSubnetPurpose(subnetDetailsList: ISubnet[] = []) {
    return head(subnetDetailsList.map(subnet => subnet.purpose)) || '';
  }
}

export const AWS_LOAD_BALANCER_DETAILS_CTRL = 'spinnaker.amazon.loadBalancer.details.controller';
module(AWS_LOAD_BALANCER_DETAILS_CTRL, [
  require('@uirouter/angularjs').default,
  APPLICATION_READ_SERVICE,
  SECURITY_GROUP_READER,
  LOAD_BALANCER_ACTIONS,
  LOAD_BALANCER_WRITE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  SUBNET_READ_SERVICE,
]).controller('awsLoadBalancerDetailsCtrl', AwsLoadBalancerDetailsController);
