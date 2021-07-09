import UIROUTER_ANGULARJS, { StateService } from '@uirouter/angularjs';
import angular, { IController, module } from 'angular';
import ANGULAR_UI_BOOTSTRAP, { IModalService } from 'angular-ui-bootstrap';
import { sortBy } from 'lodash';

import {
  Application,
  ConfirmationModalService,
  FirewallLabels,
  ISecurityGroup,
  LoadBalancerReader,
  LoadBalancerWriter,
  SecurityGroupReader,
} from '@spinnaker/core';

import { OracleLoadBalancerController } from '../configure/createLoadBalancer.controller';
import { ILoadBalancerDetails, IOracleLoadBalancer } from '../../domain/IOracleLoadBalancer';

export class OracleLoadBalancerDetailController implements IController {
  public static $inject = [
    '$scope',
    '$state',
    '$uibModal',
    'loadBalancer',
    'app',
    'securityGroupReader',
    'loadBalancerReader',
    '$q',
  ];
  constructor(
    private $scope: ng.IScope,
    private $state: StateService,
    private $uibModal: IModalService,
    private loadBalancer: ILoadBalancerDetails,
    private app: Application,
    private securityGroupReader: SecurityGroupReader,
    private loadBalancerReader: LoadBalancerReader,
    private $q: angular.IQService,
  ) {
    $scope.state = {
      loading: true,
    };
    $scope.firewallsLabel = FirewallLabels.get('Firewalls');

    const extractLoadBalancer = (): PromiseLike<any> => {
      $scope.loadBalancer = app.loadBalancers.data.filter((test: IOracleLoadBalancer) => {
        return (
          test.name === loadBalancer.name &&
          test.region === loadBalancer.region &&
          test.account === loadBalancer.accountId
        );
      })[0];

      if ($scope.loadBalancer) {
        const detailsLoader = this.loadBalancerReader.getLoadBalancerDetails(
          $scope.loadBalancer.cloudProvider,
          loadBalancer.accountId,
          loadBalancer.region,
          loadBalancer.name,
        );

        return detailsLoader.then((details) => {
          // ILoadBalancerSourceData
          $scope.state.loading = false;
          const securityGroups: ISecurityGroup[] = [];

          const filtered = details.filter((test: IOracleLoadBalancer) => {
            return test.name === loadBalancer.name;
          });

          if (filtered.length) {
            $scope.loadBalancer.elb = filtered[0];

            $scope.loadBalancer.account = loadBalancer.accountId;

            if ($scope.loadBalancer.elb.securityGroups) {
              $scope.loadBalancer.elb.securityGroups.forEach((securityGroupId: string) => {
                const match = this.securityGroupReader.getApplicationSecurityGroup(
                  this.app,
                  loadBalancer.accountId,
                  loadBalancer.region,
                  securityGroupId,
                );
                if (match) {
                  securityGroups.push(match);
                }
              });
              this.$scope.securityGroups = sortBy(securityGroups, 'name');
            }
          }
        });
      }
      if (!this.$scope.loadBalancer) {
        this.$state.go('^');
      }

      return this.$q.when(null);
    };

    this.app
      .ready()
      .then(extractLoadBalancer)
      .then(() => {
        // If the user navigates away from the view before the initial extractLoadBalancer call completes,
        // do not bother subscribing to the refresh
        if (!this.$scope.$$destroyed) {
          this.app.onRefresh($scope, extractLoadBalancer);
        }
      });
  }

  public editLoadBalancer() {
    this.$uibModal.open({
      templateUrl: require('../configure/editLoadBalancer.html'),
      controller: OracleLoadBalancerController,
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        application: () => {
          return this.app;
        },
        loadBalancer: () => {
          return angular.copy(this.$scope.loadBalancer);
        },
        isNew: () => {
          return false;
        },
      },
    });
  }

  public deleteLoadBalancer() {
    if (this.$scope.loadBalancer.instances && this.$scope.loadBalancer.instances.length) {
      return;
    }

    const taskMonitor = {
      application: this.app,
      title: 'Deleting ' + this.loadBalancer.name,
    };

    const command = {
      cloudProvider: 'oracle',
      loadBalancerName: this.$scope.loadBalancer.name,
      credentials: this.$scope.loadBalancer.account,
      region: this.loadBalancer.region,
      application: this.app.name,
      loadBalancerId: this.$scope.loadBalancer.id,
    };

    const submitMethod = () => LoadBalancerWriter.deleteLoadBalancer(command, this.app);

    ConfirmationModalService.confirm({
      header: 'Really delete ' + this.loadBalancer.name + '?',
      buttonText: 'Delete ' + this.loadBalancer.name,
      account: this.loadBalancer.accountId,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod,
    });
  }
}

export const ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER = 'spinnaker.oracle.loadBalancerDetail.controller';
module(ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER, [UIROUTER_ANGULARJS, ANGULAR_UI_BOOTSTRAP as any]).controller(
  'oracleLoadBalancerDetailCtrl',
  OracleLoadBalancerDetailController,
);
