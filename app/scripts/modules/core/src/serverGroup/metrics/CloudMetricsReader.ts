import { IPromise } from 'angular';

import { API } from 'core/api/ApiService';
import { ICloudMetricDescriptor, ICloudMetricStatistics } from 'core/domain';

export class CloudMetricsReader {
  public static listMetrics(
    provider: string,
    account: string,
    region: string,
    filters: any,
  ): IPromise<ICloudMetricDescriptor[]> {
    return API.all('cloudMetrics').all(provider).all(account).all(region).withParams(filters).getList();
  }

  public static getMetricStatistics(
    provider: string,
    account: string,
    region: string,
    name: string,
    filters: any,
  ): IPromise<ICloudMetricStatistics> {
    return API.all('cloudMetrics')
      .all(provider)
      .all(account)
      .all(region)
      .one(name, 'statistics')
      .withParams(filters)
      .get();
  }
}
