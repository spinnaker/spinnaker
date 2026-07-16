import { useEffect, useRef, useState } from 'react';

import { GceLoadBalancerDataController, gceLoadBalancerDataReaders } from './gceLoadBalancerData';
import type { IGceLoadBalancerDataReaders, IGceLoadBalancerDataState } from './gceLoadBalancerData';

export interface IUseGceLoadBalancerDataResult extends IGceLoadBalancerDataState {
  refresh: () => void;
}

export function useGceLoadBalancerData(
  account: string,
  readers: IGceLoadBalancerDataReaders = gceLoadBalancerDataReaders,
): IUseGceLoadBalancerDataResult {
  const controllerRef = useRef<GceLoadBalancerDataController | null>(null);
  if (!controllerRef.current) {
    controllerRef.current = new GceLoadBalancerDataController(readers);
  }
  const controller = controllerRef.current;
  const [state, setState] = useState<IGceLoadBalancerDataState>(controller.getState());

  useEffect(() => {
    const unsubscribe = controller.subscribe(setState);
    return () => {
      unsubscribe();
      controller.dispose();
    };
  }, [controller]);

  useEffect(() => {
    controller.load(account);
  }, [account, controller]);

  return { ...state, refresh: () => controller.load(account) };
}
