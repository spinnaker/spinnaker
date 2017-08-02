import { IServerGroup, IInstance } from 'core/domain';

export interface IInstanceListProps {
  hasDiscovery: boolean;
  hasLoadBalancers: boolean;
  instances: IInstance[];
  sortFilter: any;
  serverGroup: IServerGroup;
}

export const instanceListBindings: Record<keyof IInstanceListProps, string> = {
  hasDiscovery: '=',
  hasLoadBalancers: '=',
  instances: '=',
  sortFilter: '=',
  serverGroup: '=',
};
