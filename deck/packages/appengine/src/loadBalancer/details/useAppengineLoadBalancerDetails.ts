import { useEffect, useState } from 'react';

import type { IUseDetailsHookProps, UseDetailsResult } from '@spinnaker/core';

import type { IAppengineLoadBalancer } from '../../domain';

export function useAppengineLoadBalancerDetails({
  app,
  loadBalancerParams,
  autoClose,
}: IUseDetailsHookProps): UseDetailsResult<IAppengineLoadBalancer> {
  const [data, setData] = useState<IAppengineLoadBalancer>();
  const [loading, setLoading] = useState(true);

  const refetch = async () => {
    setLoading(true);
    await app.getDataSource('loadBalancers').ready();
    const loadBalancer = app.getDataSource('loadBalancers').data.find((candidate: IAppengineLoadBalancer) => {
      return candidate.name === loadBalancerParams.name && candidate.account === loadBalancerParams.accountId;
    });

    setData(loadBalancer);
    setLoading(false);

    if (!loadBalancer) {
      autoClose();
    }
  };

  useEffect(() => {
    refetch();
    const unsubscribe = app.getDataSource('loadBalancers').onRefresh(null, refetch);
    return () => unsubscribe();
  }, [loadBalancerParams.name, loadBalancerParams.accountId, loadBalancerParams.region]);

  return { data, loading, error: null, refetch };
}
