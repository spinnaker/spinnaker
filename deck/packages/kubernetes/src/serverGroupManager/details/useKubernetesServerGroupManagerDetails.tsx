import { useEffect, useState } from 'react';

import type { IManifest, IServerGroupManager } from '@spinnaker/core';
import { ManifestReader } from '@spinnaker/core';

import type { IKubernetesServerGroupManagerDetailsProps } from './ServerGroupManagerDetails';
import type { IKubernetesServerGroupManager } from '../../interfaces';

interface IServerGroupManagerDetailsState {
  serverGroupManager?: IKubernetesServerGroupManager;
  manifest?: IManifest;
  loading: boolean;
}

export function useKubernetesServerGroupManagerDetails(
  props: IKubernetesServerGroupManagerDetailsProps,
  autoClose: () => void,
): [IKubernetesServerGroupManager | undefined, IManifest | undefined, boolean] {
  const [state, setState] = useState<IServerGroupManagerDetailsState>({ loading: true });
  const { serverGroupManager: params, app } = props;
  const { accountId, region, name } = params;
  const loadedRequestedManager =
    state.serverGroupManager?.name === name &&
    state.serverGroupManager?.region === region &&
    state.serverGroupManager?.account === accountId;

  useEffect(() => {
    if (loadedRequestedManager) {
      return undefined;
    }

    let cancelled = false;
    const dataSource = app.getDataSource('serverGroupManagers');

    setState((current) =>
      current.loading && !current.serverGroupManager && !current.manifest ? current : { loading: true },
    );

    dataSource.ready().then(() => {
      if (cancelled) {
        return;
      }

      const serverGroupManagerDetails = dataSource.data.find(
        (manager: IServerGroupManager) =>
          manager.name === name && manager.region === region && manager.account === accountId,
      );

      if (!serverGroupManagerDetails) {
        autoClose();
        return;
      }

      ManifestReader.getManifest(accountId, region, name)
        .then((manifest: IManifest) => {
          if (!cancelled) {
            setState({ manifest, serverGroupManager: serverGroupManagerDetails, loading: false });
          }
        })
        .catch((error) => {
          if (!cancelled) {
            console.error('Error fetching manifest:', error);
            autoClose();
          }
        });
    });

    return () => {
      cancelled = true;
    };
  }, [accountId, app, autoClose, loadedRequestedManager, name, region]);

  return [state.serverGroupManager, state.manifest, state.loading];
}
