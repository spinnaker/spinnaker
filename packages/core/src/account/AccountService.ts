import { IQResolveReject } from 'angular';
import { chain, intersection, uniq, zipObject } from 'lodash';
import { $log, $q } from 'ngimport';
import { defer as observableDefer, from as observableFrom, Observable } from 'rxjs';
import { map, publishReplay, refCount } from 'rxjs/operators';

import { REST } from '../api/ApiService';
import { Application } from '../application/application.model';
import { CloudProviderRegistry } from '../cloudProvider/CloudProviderRegistry';
import { SETTINGS } from '../config/settings';
import { ILoadBalancer, IServerGroup } from '../domain';

export interface IRegion {
  account?: string;
  availabilityZones?: string[];
  deprecated?: boolean;
  endpoint?: string;
  faultDomains?: string[];
  name: string;
  preferredZones?: string[];
}

export interface IAccount {
  accountId: string;
  name: string;
  requiredGroupMembership: string[];
  type: string;
}

export interface IArtifactAccount {
  name: string;
  types: string[];
}

export interface IAccountDetails extends IAccount {
  accountType: string;
  authorized: boolean;
  awsAccount?: string;
  awsVpc?: string;
  bastionHost?: string;
  challengeDestructiveActions: boolean;
  cloudProvider: string;
  defaultKeyPair?: string;
  environment: string;
  primaryAccount: boolean;
  regions: IRegion[];
  registry?: string;
  namespaces?: string[];
  spinnakerKindMap?: { [k: string]: string };
}

export interface IAggregatedAccounts {
  [credentials: string]: IAccountDetails;
}

export interface IZone {
  [key: string]: string[];
}

export interface IAccountZone {
  [key: string]: IZone;
}

export class AccountService {
  public static accounts$: Observable<IAccountDetails[]>;
  public static providers$: Observable<string[]>;

  public static initialize(): void {
    this.accounts$ = observableDefer(() => {
      const promise = REST('/credentials').useCache().query({ expand: true }).get<IAccountDetails[]>();
      return observableFrom(promise);
    }).pipe(publishReplay(1), refCount());

    this.providers$ = AccountService.accounts$.pipe(
      map((accounts: IAccountDetails[]) => {
        const providersFromAccounts: string[] = uniq(accounts.map((account) => account.type));
        return intersection(providersFromAccounts, CloudProviderRegistry.listRegisteredProviders());
      }),
    );
  }

  public static challengeDestructiveActions(account: string): PromiseLike<boolean> {
    return $q((resolve: IQResolveReject<boolean>) => {
      if (account) {
        this.getAccountDetails(account)
          .then((details: IAccountDetails) => {
            if (details) {
              resolve(details.challengeDestructiveActions);
            } else {
              resolve(false);
            }
          })
          .catch(() => resolve(false));
      } else {
        resolve(false);
      }
    });
  }

  public static getArtifactAccounts(): PromiseLike<IArtifactAccount[]> {
    return REST('/artifacts/credentials').useCache().get();
  }

  public static getAccountDetails(account: string): PromiseLike<IAccountDetails> {
    return this.listAllAccounts().then((accounts) => accounts.find((a) => a.name === account));
  }

  public static getAllAccountDetailsForProvider(provider: string): PromiseLike<IAccountDetails[]> {
    return this.listAccounts(provider).catch((error: any) => {
      $log.warn(`Failed to load accounts for provider "${provider}"; exception:`, error);
      return [];
    });
  }

  public static getAvailabilityZonesForAccountAndRegion(
    provider: string,
    account: string,
    region: string,
  ): PromiseLike<string[]> {
    return this.getPreferredZonesByAccount(provider).then(
      (result: IAccountZone) => (result[account] && result[account][region]) || [],
    );
  }

  public static getCredentialsKeyedByAccount(provider: string = null): PromiseLike<IAggregatedAccounts> {
    return this.listAllAccounts(provider).then((accounts: IAccountDetails[]) => {
      const names: string[] = accounts.map((account: IAccount) => account.name);
      return zipObject<IAccountDetails, IAggregatedAccounts>(names, accounts);
    });
  }

  public static getPreferredZonesByAccount(provider: string): PromiseLike<IAccountZone> {
    const preferred: IAccountZone = {};
    return this.getAllAccountDetailsForProvider(provider).then((accounts: IAccountDetails[]) => {
      accounts.forEach((account: IAccountDetails) => {
        preferred[account.name] = {};
        account.regions.forEach((region: IRegion) => {
          let zones: string[] = region.availabilityZones;
          if (region.preferredZones) {
            zones = intersection(region.preferredZones, region.availabilityZones);
          }

          preferred[account.name][region.name] = zones;
        });
      });

      return preferred;
    });
  }

  public static getRegionsForAccount(account: string): PromiseLike<IRegion[]> {
    return this.getAccountDetails(account).then((details: IAccountDetails) => (details ? details.regions : []));
  }

  public static getUniqueAttributeForAllAccounts(provider: string, attribute: string): PromiseLike<string[]> {
    return this.getCredentialsKeyedByAccount(provider).then((credentials: IAggregatedAccounts) => {
      return chain(credentials)
        .map(attribute)
        .flatten()
        .compact()
        .map((region: IRegion) => region.name || region)
        .uniq()
        .value() as string[];
    });
  }

  public static listAllAccounts(provider: string = null): PromiseLike<IAccountDetails[]> {
    return $q
      .when(this.accounts$.toPromise())
      .then((accounts: IAccountDetails[]) => accounts.filter((account) => !provider || account.type === provider));
  }

  public static listAccounts(provider: string = null): PromiseLike<IAccountDetails[]> {
    return this.listAllAccounts(provider).then((accounts) =>
      accounts.filter((account) => account.authorized !== false),
    );
  }

  public static applicationAccounts(application: Application = null): PromiseLike<IAccountDetails[]> {
    return $q.all([this.listProviders(application), this.listAccounts()]).then(([providers, accounts]) => {
      return providers.reduce((memo, p) => {
        return memo.concat(accounts.filter((acc) => acc.cloudProvider === p));
      }, [] as IAccountDetails[]);
    });
  }

  public static listProviders$(application: Application = null): Observable<string[]> {
    return this.providers$.pipe(
      map((available: string[]) => {
        if (!application) {
          return available;
        } else if (application.attributes.cloudProviders.length) {
          return intersection(available, application.attributes.cloudProviders);
        } else if (SETTINGS.defaultProviders) {
          return intersection(available, SETTINGS.defaultProviders);
        } else {
          return available;
        }
      }),
      map((results) => results.sort()),
    );
  }

  public static listProviders(application: Application = null): PromiseLike<string[]> {
    return $q.when(this.listProviders$(application).toPromise());
  }

  public static getAccountForInstance(
    cloudProvider: string,
    instanceId: string,
    app: Application,
  ): PromiseLike<string> {
    return app.ready().then(() => {
      const serverGroups = app.getDataSource('serverGroups').data as IServerGroup[];
      const loadBalancers = app.getDataSource('loadBalancers').data as ILoadBalancer[];
      const loadBalancerServerGroups = loadBalancers
        .map((lb) => lb.serverGroups)
        .reduce((acc, sg) => acc.concat(sg), []);

      const hasInstance = (obj: IServerGroup | ILoadBalancer) => {
        return (
          obj.cloudProvider === cloudProvider && (obj.instances || []).some((instance) => instance.id === instanceId)
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
}

AccountService.initialize();
