import { useMemo } from 'react';

import type { IManifest, IUseDetailsHookProps, UseDetailsHook, UseDetailsResult } from '@spinnaker/core';
import { ManifestReader, useData, useDataSource } from '@spinnaker/core';

import type { IKubernetesLoadBalancer } from '../../interfaces';

export const useKubernetesLoadBalancerDetails: UseDetailsHook<IKubernetesLoadBalancer> = (
  props: IUseDetailsHookProps,
): UseDetailsResult<IKubernetesLoadBalancer> => {
  const { loadBalancerParams, app, autoClose } = props;
  const { name, accountId, region } = loadBalancerParams;

  const dataSource = app.getDataSource('loadBalancers');
  const {
    data: loadBalancers,
    loaded: isLoadBalancersLoaded,
    refresh: refreshLoadBalancers,
    error: loadBalancersError,
  } = useDataSource<IKubernetesLoadBalancer[]>(dataSource);

  const { result: manifest, status: manifestStatus, refresh: refreshManifest, error: manifestError } = useData(
    () => ManifestReader.getManifest(accountId, region, name),
    {} as IManifest,
    [],
  );

  const loading = !isLoadBalancersLoaded || manifestStatus !== 'RESOLVED';

  const error = loadBalancersError || manifestError || undefined;

  const data = useMemo<IKubernetesLoadBalancer | undefined>(() => {
    if (loading || error) {
      return undefined;
    }
    const details = loadBalancers.find(
      (lb: IKubernetesLoadBalancer) => lb.name === name && lb.account === accountId && lb.region === region,
    );
    if (!details) {
      autoClose();
      return undefined;
    }
    return { ...details, manifest };
  }, [isLoadBalancersLoaded, loadBalancers, manifestStatus, manifest]);

  const refetch = async () => {
    refreshLoadBalancers();
    refreshManifest();
  };

  return { data, loading, error, refetch };
};
