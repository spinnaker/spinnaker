import { REST } from '../../api/ApiService';
import { ICloudMetricDescriptor, ICloudMetricStatistics } from '../../domain';

export class CloudMetricsReader {
  public static listMetrics(
    provider: string,
    account: string,
    region: string,
    filters: any,
  ): PromiseLike<ICloudMetricDescriptor[]> {
    return REST('/cloudMetrics').path(provider, account, region).query(filters).get();
  }

  public static getMetricStatistics(
    provider: string,
    account: string,
    region: string,
    name: string,
    filters: any,
  ): PromiseLike<ICloudMetricStatistics> {
    return REST('/cloudMetrics').path(provider, account, region, name, 'statistics').query(filters).get();
  }
}
