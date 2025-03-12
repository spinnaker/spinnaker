import type { IController, IQService } from 'angular';
import { module } from 'angular';
import { flattenDeep } from 'lodash';

import type { Application, ILoadBalancer } from '@spinnaker/core';
import { InstanceReader, RecentHistoryService } from '@spinnaker/core';
import type { ICloudrunInstance } from '../../common/domain';

interface InstanceFromStateParams {
  instanceId: string;
}

interface InstanceManager {
  account: string;
  region: string;
  category: string; // e.g., serverGroup, loadBalancer.
  name: string; // Parent resource name, not instance name.
  instances: ICloudrunInstance[];
}

class CloudrunInstanceDetailsController implements IController {
  public state = { loading: true };
  public instance: ICloudrunInstance;
  public instanceIdNotFound: string;
  public upToolTip = "A Cloud Run instance is 'Up' if a load balancer is directing traffic to its server group.";
  public outOfServiceToolTip = `
    A Cloud Run instance is 'Out Of Service' if no load balancers are directing traffic to its server group.`;

  public static $inject = ['$q', 'app', 'instance'];

  constructor(private $q: IQService, private app: Application, instance: InstanceFromStateParams) {
    this.app
      .ready()
      .then(() => this.retrieveInstance(instance))
      .then((instanceDetails) => {
        this.instance = instanceDetails;
        this.state.loading = false;
      })
      .catch(() => {
        this.instanceIdNotFound = instance.instanceId;
        this.state.loading = false;
      });
  }

  private retrieveInstance(instance: InstanceFromStateParams): PromiseLike<ICloudrunInstance> {
    const instanceLocatorPredicate = (dataSource: InstanceManager) => {
      return dataSource.instances.some((possibleMatch) => possibleMatch.id === instance.instanceId);
    };

    const dataSources: InstanceManager[] = flattenDeep([
      this.app.getDataSource('serverGroups').data,
      this.app.getDataSource('loadBalancers').data,
      this.app.getDataSource('loadBalancers').data.map((loadBalancer: ILoadBalancer) => loadBalancer.serverGroups),
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
      ).then((instanceDetails: ICloudrunInstance) => {
        instanceDetails.account = instanceManager.account;
        instanceDetails.region = instanceManager.region;
        return instanceDetails;
      });
    } else {
      return this.$q.reject();
    }
  }
}

export const CLOUDRUN_INSTANCE_DETAILS_CTRL = 'spinnaker.cloudrun.instanceDetails.controller';
module(CLOUDRUN_INSTANCE_DETAILS_CTRL, []).controller('cloudrunInstanceDetailsCtrl', CloudrunInstanceDetailsController);
