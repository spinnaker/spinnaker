import { IController, IPromise, IQService, IScope, module } from 'angular';
import { react2angular } from 'react2angular';

import { CloudFoundryInstanceDetails } from './CloudFoundryInstanceDetails';
import {
  Application,
  ConfirmationModalService,
  InstanceReader,
  InstanceWriter,
  RecentHistoryService,
} from '@spinnaker/core';
import { ICloudFoundryInstance } from 'cloudfoundry/domain';
import { flattenDeep } from 'lodash';

interface InstanceFromStateParams {
  instanceId: string;
}

interface InstanceManager {
  account: string;
  region: string;
  category: string; // e.g., serverGroup, loadBalancer.
  name: string; // Parent resource name, not instance name.
  instances: ICloudFoundryInstance[];
}

class CloudFoundryInstanceDetailsCtrl implements IController {
  public static $inject = ['$scope', 'app', 'instance', 'instanceWriter', 'confirmationModalService', '$q'];
  constructor(
    public $scope: IScope,
    private app: Application,
    private instance: InstanceFromStateParams,
    private instanceWriter: InstanceWriter,
    private confirmationModalService: ConfirmationModalService,
    private $q: IQService,
  ) {
    'ngInject';
    this.$scope.application = this.app;
    this.$scope.instanceWriter = this.instanceWriter;
    this.$scope.confirmationModalService = this.confirmationModalService;
    this.$scope.qService = this.$q;
    this.$scope.loading = true;
    this.app
      .ready()
      .then(() => this.retrieveInstance(this.instance))
      .then(instanceDetails => {
        this.$scope.instance = instanceDetails;
        this.$scope.loading = false;
      })
      .catch(() => {
        this.$scope.instanceIdNotFound = this.instance.instanceId;
        this.$scope.loading = false;
      });
  }
  private retrieveInstance(instance: InstanceFromStateParams): IPromise<ICloudFoundryInstance> {
    const instanceLocatorPredicate = (dataSource: InstanceManager) => {
      return dataSource.instances.some(possibleMatch => possibleMatch.id === instance.instanceId);
    };

    const dataSources: InstanceManager[] = flattenDeep([
      this.app.getDataSource('serverGroups').data,
      this.app.getDataSource('loadBalancers').data,
      this.app.getDataSource('loadBalancers').data.map(loadBalancer => loadBalancer.serverGroups),
    ]);

    const instanceManager = dataSources.find(instanceLocatorPredicate);

    if (instanceManager) {
      const recentHistoryExtraData: { [key: string]: string } = {
        region: instanceManager.region,
        account: instanceManager.account,
      };
      if (instanceManager.category === 'serverGroup') {
        recentHistoryExtraData.serverGroup = instanceManager.name;
      }
      RecentHistoryService.addExtraDataToLatest('instances', recentHistoryExtraData);

      return InstanceReader.getInstanceDetails(
        instanceManager.account,
        instanceManager.region,
        instance.instanceId,
      ).then((instanceDetails: ICloudFoundryInstance) => {
        instanceDetails.account = instanceManager.account;
        instanceDetails.region = instanceManager.region;
        return instanceDetails;
      });
    } else {
      return this.$q.reject();
    }
  }
}

export const CLOUD_FOUNDRY_INSTANCE_DETAILS = 'spinnaker.cloudfoundry.instanceDetails';
module(CLOUD_FOUNDRY_INSTANCE_DETAILS, [])
  .component(
    'cfInstanceDetails',
    react2angular(CloudFoundryInstanceDetails, [
      'application',
      'confirmationModalService',
      'instance',
      'instanceIdNotFound',
      'instanceWriter',
      'loading',
    ]),
  )
  .controller('cfInstanceDetailsCtrl', CloudFoundryInstanceDetailsCtrl);
