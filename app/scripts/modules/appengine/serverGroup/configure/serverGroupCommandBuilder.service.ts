import {module, IQService, IPromise} from 'angular';
import {get, intersection} from 'lodash';

import {Application} from 'core/application/application.model';
import {AccountService, ACCOUNT_SERVICE} from 'core/account/account.service';
import {IAppengineAccount} from 'appengine/domain/index';

export interface IAppengineServerGroupCommand {
  application?: string;
  stack?: string;
  freeFormDetails?: string;
  appYamlPath?: string;
  branch?: string;
  repositoryUrl?: string;
  credentials: string;
  region: string;
  selectedProvider: string;
  promote?: boolean;
  stopPreviousVersion?: boolean;
  type?: string;
  backingData: any;
  viewState: any;
}

class AppengineServerGroupCommandBuilder {
  static get $inject() { return ['$q', 'accountService', 'settings']; }

  constructor(private $q: IQService, private accountService: AccountService, private settings: any) { }

  public buildNewServerGroupCommand(app: Application, selectedProvider = 'appengine'): IPromise<IAppengineServerGroupCommand> {
    let dataToFetch = {
      accounts: this.accountService.getAllAccountDetailsForProvider('appengine'),
    };

    let viewState = {
      mode: 'create',
    };

    return this.$q.all(dataToFetch)
      .then((backingData: any) => {
        let credentials: string = this.getCredentials(backingData.accounts, app);
        let region: string = this.getRegion(backingData.accounts, credentials);

        return {
          application: app.name,
          backingData,
          viewState,
          credentials,
          region,
          selectedProvider,
        } as IAppengineServerGroupCommand;
      });
  }

  private getCredentials(accounts: IAppengineAccount[], application: Application): string {
    let accountNames: string[] = (accounts || []).map((account) => account.name);
    let defaultCredentials: string = get(this.settings, 'settings.providers.gce.defaults.account') as string;
    let firstApplicationAccount: string = intersection(application.accounts || [], accountNames)[0];

    return accountNames.includes(defaultCredentials) ?
      defaultCredentials :
      (firstApplicationAccount || 'my-appengine-account');
  }

  private getRegion(accounts: IAppengineAccount[], credentials: string): string {
    let account = accounts.find((_account) => _account.name === credentials);
    return account ? account.region : null;
  }
}

export const APPENGINE_SERVER_GROUP_COMMAND_BUILDER = 'spinnaker.appengine.serverGroupCommandBuilder.service';

module(APPENGINE_SERVER_GROUP_COMMAND_BUILDER, [
  ACCOUNT_SERVICE,
]).service('appengineServerGroupCommandBuilder', AppengineServerGroupCommandBuilder);
