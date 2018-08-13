import { cloneDeep } from 'lodash';
import { dump, loadAll } from 'js-yaml';
import { $q } from 'ngimport';
import { IPromise } from 'angular';

import { AccountService, Application, IMoniker, IAccount, IAccountDetails } from '@spinnaker/core';

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
  source?: string;
  versioned?: boolean;
}

export interface IKubernetesManifestCommandMetadata {
  manifestText: string;
  yamlError: boolean;
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

  public static copyAndCleanCommand(
    metadata: IKubernetesManifestCommandMetadata,
    input: IKubernetesManifestCommand,
  ): IKubernetesManifestCommand {
    const command = cloneDeep(input);
    command.manifests = [];
    loadAll(metadata.manifestText, doc => command.manifests.push(doc));
    delete command.source;
    return command;
  }

  public static buildNewManifestCommand(
    app: Application,
    sourceManifest?: any,
    sourceMoniker?: IMoniker,
    sourceAccount?: string,
  ): IPromise<IKubernetesManifestCommandData> {
    const dataToFetch = {
      accounts: AccountService.getAllAccountDetailsForProvider('kubernetes', 'v2'),
      artifactAccounts: AccountService.getArtifactAccounts(),
    };

    // TODO(dpeach): if no callers of this method are Angular controllers,
    // $q.all may be safely replaced with Promise.all.
    return $q.all(dataToFetch).then((backingData: { accounts: IAccountDetails[]; artifactAccounts: IAccount[] }) => {
      const { accounts, artifactAccounts } = backingData;

      const account = accounts.some(a => a.name === sourceAccount)
        ? accounts.find(a => a.name === sourceAccount).name
        : accounts.length
          ? accounts[0].name
          : null;

      let manifestArtifactAccount: string = null;
      const [artifactAccountData] = artifactAccounts;
      if (artifactAccountData) {
        manifestArtifactAccount = artifactAccountData.name;
      }

      const manifest: any = null;
      const manifests: any = null;
      const manifestText = !sourceManifest ? '' : dump(sourceManifest);
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
          manifest,
          manifests,
          relationships,
          moniker,
          account,
          versioned,
          manifestArtifactAccount,
        },
        metadata: {
          backingData,
          manifestText,
        },
      } as IKubernetesManifestCommandData;
    });
  }
}
