import { useEffect, useState } from 'react';

import type { IManifest, IServerGroupManager } from '@spinnaker/core';
import { ManifestReader } from '@spinnaker/core';

import type { IKubernetesServerGroupManagerDetailsProps } from './ServerGroupManagerDetails';
import type { IKubernetesServerGroupManager } from '../../interfaces';

export function useKubernetesServerGroupManagerDetails(
  props: IKubernetesServerGroupManagerDetailsProps,
  autoClose: () => void,
): [IKubernetesServerGroupManager | undefined, IManifest | undefined, boolean] {
  const [serverGroupManager, setServerGroupManager] = useState<IKubernetesServerGroupManager | undefined>();
  const [manifest, setManifest] = useState<IManifest | undefined>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (serverGroupManager) {
      return;
    }

    const { serverGroupManager: params, app } = props;
    const { accountId, region, name } = params;

    setLoading(true);

    const serverGroupManagerDetails = app
      .getDataSource('serverGroupManagers')
      .data.find(
        (manager: IServerGroupManager) =>
          manager.name === name && manager.region === region && manager.account === accountId,
      );

    if (!serverGroupManagerDetails) {
      autoClose();
      return;
    }

    ManifestReader.getManifest(accountId, region, name)
      .then((manifest: IManifest) => {
        setManifest(manifest);
        setServerGroupManager(serverGroupManagerDetails);
      })
      .catch((error) => {
        console.error('Error fetching manifest:', error);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [autoClose, props, serverGroupManager]);

  return [serverGroupManager, manifest, loading];
}
