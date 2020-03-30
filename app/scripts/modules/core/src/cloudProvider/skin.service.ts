import { IPromise } from 'angular';
import { map } from 'lodash';
import { $log } from 'ngimport';

import { AccountService, IAccountDetails } from 'core/account';
import { CloudProviderRegistry } from './CloudProviderRegistry';
import { ILoadBalancer, IServerGroup } from 'core/domain';
import { IMoniker } from 'core/naming';
import { Application } from 'core/application';

export class SkinService {
  public static getValue(cloudProvider: string, accountName: string, key: string): IPromise<any> {
    return this.getAccounts().then(accounts => {
      const account = accounts.find(a => a.name === accountName && a.cloudProvider === cloudProvider);
      return account && account.skin
        ? CloudProviderRegistry.getValue(cloudProvider, key, account.skin)
        : CloudProviderRegistry.getValue(cloudProvider, key);
    });
  }

  public static getInstanceSkin(cloudProvider: string, instanceId: string, app: Application): IPromise<string> {
    return this.getAccounts().then(accounts => {
      const skins = accounts.reduce((versions, account) => {
        if (account.cloudProvider === cloudProvider && !!account.skin) {
          versions.add(account.skin);
        }
        return versions;
      }, new Set<string>());

      if (skins.size === 0) {
        // Rely on the CloudProviderRegistry to return the default skin implementation.
        return null;
      } else if (skins.size === 1) {
        return Array.from(skins)[0];
      }

      return app.ready().then(() => {
        for (const serverGroup of app.getDataSource('serverGroups').data as IServerGroup[]) {
          if (
            serverGroup.cloudProvider === cloudProvider &&
            (serverGroup.instances || []).some(instance => instance.id === instanceId)
          ) {
            return this.mapAccountToSkin(serverGroup.account);
          }
        }
        for (const loadBalancer of app.getDataSource('loadBalancers').data as ILoadBalancer[]) {
          if (loadBalancer.cloudProvider === cloudProvider) {
            if ((loadBalancer.instances || []).some(instance => instance.id === instanceId)) {
              return this.mapAccountToSkin(loadBalancer.account);
            }
            // Hit a crazy Babel bug - can't return from a nested for...of loop.
            for (let i = 0; i < (loadBalancer.serverGroups || []).length; i++) {
              const serverGroup = loadBalancer.serverGroups[i];
              if (
                serverGroup.isDisabled &&
                (serverGroup.instances || []).some(instance => instance.id === instanceId)
              ) {
                return this.mapAccountToSkin(loadBalancer.account);
              }
            }
          }
        }
        return null;
      });
    });
  }

  public static getAccountForInstance(cloudProvider: string, instanceId: string, app: Application): IPromise<string> {
    return app.ready().then(() => {
      const serverGroups = app.getDataSource('serverGroups').data as IServerGroup[];
      const loadBalancers = app.getDataSource('loadBalancers').data as ILoadBalancer[];
      const loadBalancerServerGroups = loadBalancers.map(lb => lb.serverGroups).reduce((acc, sg) => acc.concat(sg), []);

      const hasInstance = (obj: IServerGroup | ILoadBalancer) => {
        return (
          obj.cloudProvider === cloudProvider && (obj.instances || []).some(instance => instance.id === instanceId)
        );
      };

      const all: Array<IServerGroup | ILoadBalancer> = []
        .concat(serverGroups)
        .concat(loadBalancers)
        .concat(loadBalancerServerGroups);
      const found = all.find(hasInstance);
      return found && found.account;
    });
  }

  public static getMonikerForInstance(cloudProvider: string, instanceId: string, app: Application): IPromise<IMoniker> {
    return app.ready().then(() => {
      const serverGroups = app.getDataSource('serverGroups').data as IServerGroup[];
      const loadBalancers = app.getDataSource('loadBalancers').data as ILoadBalancer[];
      const loadBalancerServerGroups = loadBalancers.map(lb => lb.serverGroups).reduce((acc, sg) => acc.concat(sg), []);

      const hasInstance = (obj: IServerGroup | ILoadBalancer) => {
        return (
          obj.cloudProvider === cloudProvider && (obj.instances || []).some(instance => instance.id === instanceId)
        );
      };

      const all: Array<IServerGroup | ILoadBalancer> = []
        .concat(serverGroups)
        .concat(loadBalancers)
        .concat(loadBalancerServerGroups);
      const found = all.find(hasInstance);
      return found && found.moniker;
    });
  }

  public static getAccounts(): IPromise<IAccountDetails[]> {
    return AccountService.getCredentialsKeyedByAccount()
      .then(aggregatedAccounts => map(aggregatedAccounts))
      .catch(e => {
        $log.warn('Could not initialize accounts: ', e && e.message);
        return [];
      });
  }

  private static mapAccountToSkin(accountName: string): IPromise<string> {
    return this.getAccounts().then(accounts => {
      const account = accounts.find(a => a.name === accountName);
      return account ? account.skin : null;
    });
  }
}
