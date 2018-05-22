import { cloneDeep } from 'lodash';
import { dump, loadAll } from 'js-yaml';
import { AccountService, Application, IMoniker } from '@spinnaker/core';

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
  ): Promise<IKubernetesManifestCommandData> {
    const dataToFetch = [
      AccountService.getAllAccountDetailsForProvider('kubernetes', 'v2'),
      AccountService.getArtifactAccounts(),
    ];

    return Promise.all(dataToFetch).then(([accounts, artifactAccounts]) => {
      const backingData = {
        accounts,
        artifactAccounts,
      };
      const accountData = backingData.accounts[0];
      let account: string = null;
      if (accountData) {
        account = accountData.name;
      }

      let manifestArtifactAccount: string = null;
      const artifactAccountData = backingData.artifactAccounts[0];
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
