import type { IAmazonServerGroup, IAmazonWarmPoolConfiguration } from '../../../domain';

export class WarmPoolService {
  public static getWarmPoolConfiguration(serverGroup: IAmazonServerGroup): IAmazonWarmPoolConfiguration | undefined {
    return serverGroup.asg?.warmPoolConfiguration;
  }

  public static isEnabled(serverGroup: IAmazonServerGroup): boolean {
    return !!WarmPoolService.getWarmPoolConfiguration(serverGroup);
  }

  public static poolStates(): Array<IAmazonWarmPoolConfiguration['poolState']> {
    return ['Stopped', 'Running', 'Hibernated'];
  }
}
