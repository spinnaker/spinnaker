import { load } from 'js-yaml';
import { cloneDeep, has } from 'lodash';

import type { Application, IAccountDetails, IArtifactAccount, IMoniker } from '@spinnaker/core';
import { AccountService } from '@spinnaker/core';

import { ManifestSource } from './ManifestSource';

const LAST_APPLIED_CONFIGURATION = 'kubectl.kubernetes.io/last-applied-configuration';

export interface IKubernetesManifestCommandData {
  command: IKubernetesManifestCommand;
  metadata: IKubernetesManifestCommandMetadata;
}

export interface IKubernetesManifestCommand {
  account: string;
  cloudProvider: string;
  manifest: any; // deprecated
  manifests: any[];
  relationships: IKubernetesManifestSpinnakerRelationships;
  moniker: IMoniker;
  manifestArtifactId?: string;
  manifestArtifactAccount?: string;
  source: ManifestSource;
  versioned?: boolean;
}

export interface IKubernetesManifestCommandMetadata {
  backingData: any;
}

export interface IKubernetesManifestSpinnakerRelationships {
  loadBalancers?: string[];
  securityGroups?: string[];
}

export class KubernetesManifestCommandBuilder {
  // TODO(lwander) add explanatory error messages
  public static manifestCommandIsValid(command: IKubernetesManifestCommand): boolean {
    if (!command.moniker) {
      return false;
    }

    if (!command.moniker.app) {
      return false;
    }

    return true;
  }

  public static copyAndCleanCommand(input: IKubernetesManifestCommand): IKubernetesManifestCommand {
    const command = cloneDeep(input);
    return command;
  }

  public static buildNewManifestCommand(
    app: Application,
    sourceManifest?: any,
    sourceMoniker?: IMoniker,
    sourceAccount?: string,
  ): PromiseLike<IKubernetesManifestCommandData> {
    if (sourceManifest != null && has(sourceManifest, ['metadata', 'annotations', LAST_APPLIED_CONFIGURATION])) {
      sourceManifest = load(sourceManifest.metadata.annotations[LAST_APPLIED_CONFIGURATION]);
    }

    return Promise.all([
      AccountService.getAllAccountDetailsForProvider('kubernetes'),
      AccountService.getArtifactAccounts(),
    ]).then(([accounts, artifactAccounts]: [IAccountDetails[], IArtifactAccount[]]) => {
      const backingData = { accounts, artifactAccounts };

      const sourceAccountDetails = accounts.find((a) => a.name === sourceAccount);
      const account = sourceAccountDetails ? sourceAccountDetails.name : accounts.length ? accounts[0].name : null;

      let manifestArtifactAccount: string = null;
      const [artifactAccountData] = artifactAccounts;
      if (artifactAccountData) {
        manifestArtifactAccount = artifactAccountData.name;
      }

      const cloudProvider = 'kubernetes';
      const moniker = sourceMoniker || {
        app: app.name,
      };

      const relationships = {
        loadBalancers: [] as string[],
        securityGroups: [] as string[],
      };

      const versioned: any = null;

      return {
        command: {
          cloudProvider,
          manifest: null,
          manifests: Array.isArray(sourceManifest) ? sourceManifest : sourceManifest != null ? [sourceManifest] : null,
          relationships,
          moniker,
          account,
          versioned,
          manifestArtifactAccount,
          source: ManifestSource.TEXT,
        },
        metadata: {
          backingData,
        },
      } as IKubernetesManifestCommandData;
    });
  }
}
