import { useMemo } from 'react';

import { useDataSource } from '@spinnaker/core';
import type { IUseDetailsHookProps, UseDetailsHook, UseDetailsResult } from '@spinnaker/core';

import type { ICloudrunLoadBalancer } from '../../common/domain';

export const useCloudrunLoadBalancerDetails: UseDetailsHook<ICloudrunLoadBalancer> = ({
  app,
  loadBalancerParams,
  autoClose,
}: IUseDetailsHookProps): UseDetailsResult<ICloudrunLoadBalancer> => {
  const dataSource = app.getDataSource('loadBalancers');
  const { data: loadBalancers, loaded, error, refresh } = useDataSource<ICloudrunLoadBalancer[]>(dataSource);
  const data = useMemo(() => {
    if (!loaded || error) {
      return undefined;
    }

    const loadBalancer = findCloudrunLoadBalancer(loadBalancers, loadBalancerParams);
    if (!loadBalancer) {
      autoClose();
      return undefined;
    }

    return loadBalancer;
  }, [
    loaded,
    error,
    loadBalancers,
    loadBalancerParams.name,
    loadBalancerParams.accountId,
    loadBalancerParams.region,
    autoClose,
  ]);

  return {
    data,
    loading: !loaded,
    error: error || null,
    refetch: async () => refresh(),
  };
};

export function findCloudrunLoadBalancer(
  loadBalancers: ICloudrunLoadBalancer[],
  loadBalancerParams: IUseDetailsHookProps['loadBalancerParams'],
) {
  return loadBalancers.find((candidate) => {
    return (
      candidate.name === loadBalancerParams.name &&
      candidate.account === loadBalancerParams.accountId &&
      candidate.region === loadBalancerParams.region
    );
  });
}
