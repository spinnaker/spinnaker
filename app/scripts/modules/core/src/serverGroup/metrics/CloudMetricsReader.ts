import { API } from 'core/api/ApiService';
import { ICloudMetricDescriptor, ICloudMetricStatistics } from 'core/domain';

export class CloudMetricsReader {
  public static listMetrics(
    provider: string,
    account: string,
    region: string,
    filters: any,
  ): PromiseLike<ICloudMetricDescriptor[]> {
    return API.path('cloudMetrics', provider, account, region).query(filters).get();
  }

  public static getMetricStatistics(
    provider: string,
    account: string,
    region: string,
    name: string,
    filters: any,
  ): PromiseLike<ICloudMetricStatistics> {
    return API.path('cloudMetrics', provider, account, region, name, 'statistics').query(filters).get();
  }
}
