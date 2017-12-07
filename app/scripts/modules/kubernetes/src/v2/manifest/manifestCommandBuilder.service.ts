import { copy, IPromise, IQService, module } from 'angular';

import { dump, load } from 'js-yaml'

import { ACCOUNT_SERVICE, AccountService, Application, IMoniker } from '@spinnaker/core';

export interface IKubernetesManifestCommandData {
  command: IKubernetesManifestCommand;
  metadata: IKubernetesManifestCommandMetadata;
}

export interface IKubernetesManifestCommand {
  account: string;
  cloudProvider: string;
  manifest: any;
  relationships: IKubernetesManifestSpinnakerRelationships;
  moniker: IMoniker;
  manifestArtifactId?: string;
  source?: string;
  versioned?: boolean;
}

export interface IKubernetesManifestCommandMetadata {
  manifestText: string;
  backingData: any;
}

export interface IKubernetesManifestSpinnakerRelationships {
  loadBalancers?: string[];
  securityGroups?: string[];
}

export class KubernetesManifestCommandBuilder {
  constructor(private $q: IQService, private accountService: AccountService) {
    'ngInject';
  }

  // TODO(lwander) add explanatory error messages
  public manifestCommandIsValid(command: IKubernetesManifestCommand): boolean {
    if (!command.moniker) {
      return false;
    }

    if (!command.moniker.app) {
      return false;
    }

    if (!command.moniker.cluster) {
      return false;
    }

    return true;
  }

  public copyAndCleanCommand(metadata: IKubernetesManifestCommandMetadata, input: IKubernetesManifestCommand): IKubernetesManifestCommand {
    const command = copy(input);
    command.manifest = load(metadata.manifestText);
    delete command.source;
    return command;
  }

  public buildNewManifestCommand(app: Application, sourceManifest?: any, sourceMoniker?: IMoniker): IPromise<IKubernetesManifestCommandData> {
    const dataToFetch = {
      accounts: this.accountService.getAllAccountDetailsForProvider('kubernetes', 'v2'),
    };

    return this.$q.all(dataToFetch)
      .then((backingData: any) => {
        const accountData = backingData.accounts[0];
        let account: string = null;
        if (accountData) {
          account = accountData.name;
        }

        const manifest: any = null;
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
            relationships,
            moniker,
            account,
            versioned,
          },
          metadata: {
            backingData,
            manifestText,
          }
        } as IKubernetesManifestCommandData;
      });
  }
}

export const KUBERNETES_MANIFEST_COMMAND_BUILDER = 'spinnaker.kubernetes.v2.manifestBuilder.service';

module(KUBERNETES_MANIFEST_COMMAND_BUILDER, [
  ACCOUNT_SERVICE,
]).service('kubernetesManifestCommandBuilder', KubernetesManifestCommandBuilder);
