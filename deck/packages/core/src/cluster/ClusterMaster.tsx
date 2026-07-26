import React, { useEffect, useState } from 'react';

import { AllClusters } from './AllClusters';
import type { Application } from '../application';
import { ClusterState } from '../state';

export interface IClusterMasterProps {
  app: Application;
}

export interface IClusterMasterState {
  initialized: boolean;
  loadError: boolean;
}

export function useClusterMasterState(app: Application): IClusterMasterState {
  const [state, setState] = useState<IClusterMasterState>({ initialized: false, loadError: false });

  useEffect(() => {
    let active = true;

    app.setActiveState(app.serverGroups);
    (ClusterState.filterModel as any).activate();
    const updateClusterGroups = (): void => {
      if (active) {
        ClusterState.filterService.updateClusterGroups(app);
      }
    };
    const unsubscribe = app.serverGroups.onRefresh(null, updateClusterGroups);

    app.serverGroups.ready().then(
      () => {
        if (active) {
          updateClusterGroups();
          setState({ initialized: true, loadError: false });
        }
      },
      (): void => {
        if (active) {
          setState({ initialized: true, loadError: true });
        }
      },
    );

    return () => {
      active = false;
      unsubscribe();
      app.setActiveState();
      ClusterState.multiselectModel.clearAll();
    };
  }, [app]);

  return state;
}

export function ClusterMaster({ app }: IClusterMasterProps) {
  const { initialized, loadError } = useClusterMasterState(app);

  return <AllClusters app={app} initialized={initialized} loadError={loadError} />;
}
