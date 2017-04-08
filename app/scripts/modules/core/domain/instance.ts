import { Health } from './health';

export class Instance {
  public id: string;
  public availabilityZone?: string;
  public account?: string;
  public region?: string;
  public cloudProvider?: string;
  public provider?: string;
  public vpcId?: string;
  public healthState?: string;
  public health: Health[];
  public launchTime: number;
  public loadBalancers?: string[];
  public serverGroup?: string;
  public zone: string;
  public hasHealthStatus?: boolean;
}
