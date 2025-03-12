import { cloneDeep } from 'lodash';
import { $q } from 'ngimport';

import type { Application, IAccountDetails, IArtifactAccount, IMoniker } from '@spinnaker/core';
import { AccountService } from '@spinnaker/core';

import { ManifestSource } from './ManifestSource';

export interface ICloudrunManifestCommandData {
  command: ICloudrunManifestCommand;
  metadata: ICloudrunManifestCommandMetadata;
}

export interface ICloudrunManifestCommand {
  account: string;
  cloudProvider: string;
  manifest: any;
  manifests: any[];
  relationships: ICloudrunManifestSpinnakerRelationships;
  moniker: IMoniker;
  manifestArtifactId?: string;
  manifestArtifactAccount?: string;
  source: ManifestSource;
  versioned?: boolean;
}

export interface ICloudrunManifestCommandMetadata {
  backingData: any;
}

export interface ICloudrunManifestSpinnakerRelationships {
  loadBalancers?: string[];
  securityGroups?: string[];
}

export class CloudrunManifestCommandBuilder {
  public static manifestCommandIsValid(command: ICloudrunManifestCommand): boolean {
    if (!command.moniker) {
      return false;
    }

    if (!command.moniker.app) {
      return false;
    }

    return true;
  }

  public static copyAndCleanCommand(input: ICloudrunManifestCommand): ICloudrunManifestCommand {
    const command = cloneDeep(input);
    return command;
  }

  public static buildNewManifestCommand(
    app: Application,
    sourceManifest?: any,
    sourceMoniker?: IMoniker,
    sourceAccount?: string,
  ): PromiseLike<ICloudrunManifestCommandData> {
    const dataToFetch = {
      accounts: AccountService.getAllAccountDetailsForProvider('cloudrun'),
      artifactAccounts: AccountService.getArtifactAccounts(),
    };

    return $q
      .all(dataToFetch)
      .then((backingData: { accounts: IAccountDetails[]; artifactAccounts: IArtifactAccount[] }) => {
        const { accounts, artifactAccounts } = backingData;

        const account = accounts.some((a) => a.name === sourceAccount)
          ? accounts.find((a) => a.name === sourceAccount).name
          : accounts.length
          ? accounts[0].name
          : null;

        let manifestArtifactAccount: string = null;
        const [artifactAccountData] = artifactAccounts;
        if (artifactAccountData) {
          manifestArtifactAccount = artifactAccountData.name;
        }

        const cloudProvider = 'cloudrun';
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
            manifests: Array.isArray(sourceManifest)
              ? sourceManifest
              : sourceManifest != null
              ? [sourceManifest]
              : null,
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
        } as ICloudrunManifestCommandData;
      });
  }
}
