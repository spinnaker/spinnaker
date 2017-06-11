import {module} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import { ICloudMetricDescriptor, ICloudMetricStatistics } from 'core/domain';

export class CloudMetricsReader {
  public constructor(private API: Api) { 'ngInject'; }

  public listMetrics(provider: string, account: string, region: string, filters: any): ng.IPromise<ICloudMetricDescriptor[]> {
    return this.API.all('cloudMetrics').all(provider).all(account).all(region)
      .withParams(filters).getList();
  }

  public getMetricStatistics(provider: string, account: string, region: string, name: string, filters: any): ng.IPromise<ICloudMetricStatistics> {
    return this.API.all('cloudMetrics').all(provider).all(account).all(region).one(name, 'statistics')
      .withParams(filters).get();
  }
}


export const CLOUD_METRICS_READ_SERVICE = 'spinnaker.core.serverGroup.metrics.read.service';

module(CLOUD_METRICS_READ_SERVICE, [
  API_SERVICE
]).service('cloudMetricsReader', CloudMetricsReader);
