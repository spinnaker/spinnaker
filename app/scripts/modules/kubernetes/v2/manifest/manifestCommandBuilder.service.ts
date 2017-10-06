import { copy, IPromise, IQService, module } from 'angular';

import { load } from 'js-yaml'

import { IMoniker } from 'core/naming/IMoniker';
import { ACCOUNT_SERVICE, AccountService, Application } from 'core';

export interface IKubernetesManifestCommand {
  account: string;
  provider: string;
  manifest: any;
  manifestText: string;
  relationships: IKubernetesManifestSpinnakerRelationships;
  moniker: IMoniker;
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

    if (!command.manifestText) {
      return false;
    }

    return true;
  }

  public copyAndCleanCommand(input: IKubernetesManifestCommand): IKubernetesManifestCommand {
    const command = copy(input);
    command.manifest = load(command.manifestText);
    delete command.manifestText;
    delete command.backingData;
    return command;
  }

  public buildNewManifestCommand(app: Application): IPromise<IKubernetesManifestCommand> {
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

        const manifest = {};
        const manifestText = '';
        const provider = 'kubernetes';
        const moniker = {
          app: app.name,
        };
        const relationships = {
          loadBalancers: [] as string[],
          securityGroups: [] as string[],
        };

        return {
          backingData,
          provider,
          manifest,
          manifestText,
          relationships,
          moniker,
          account,
        } as IKubernetesManifestCommand;
      });
  }
}

export const KUBERNETES_MANIFEST_COMMAND_BUILDER = 'spinnaker.kubernetes.v2.manifestBuilder.service';

module(KUBERNETES_MANIFEST_COMMAND_BUILDER, [
  ACCOUNT_SERVICE,
]).service('kubernetesManifestCommandBuilder', KubernetesManifestCommandBuilder);
