import { chain, intersection, zipObject, uniq } from 'lodash';
import { IPromise, IQResolveReject } from 'angular';
import { $log, $q } from 'ngimport';
import { Observable } from 'rxjs';

import { Application } from 'core/application/application.model';
import { API } from 'core/api/ApiService';
import { CloudProviderRegistry } from '../cloudProvider/CloudProviderRegistry';
import { SETTINGS } from 'core/config/settings';

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
  providerVersion?: string;
  skin?: string;
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
    this.accounts$ = Observable.defer(() => {
      const promise = API.one('credentials')
        .useCache()
        .withParams({ expand: true })
        .get();
      return Observable.fromPromise<IAccountDetails[]>(promise);
    })
      .publishReplay(1)
      .refCount();

    this.providers$ = AccountService.accounts$.map((accounts: IAccountDetails[]) => {
      const providersFromAccounts: string[] = uniq(accounts.map(account => account.type));
      return intersection(providersFromAccounts, CloudProviderRegistry.listRegisteredProviders());
    });
  }

  public static challengeDestructiveActions(account: string): IPromise<boolean> {
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

  public static getArtifactAccounts(): IPromise<IArtifactAccount[]> {
    return API.one('artifacts')
      .one('credentials')
      .useCache()
      .get();
  }

  public static getAccountDetails(account: string): IPromise<IAccountDetails> {
    return this.listAllAccounts().then(accounts => accounts.find(a => a.name === account));
  }

  public static getAllAccountDetailsForProvider(
    provider: string,
    providerVersion: string = null,
  ): IPromise<IAccountDetails[]> {
    return this.listAllAccounts(provider, providerVersion).catch((error: any) => {
      $log.warn(`Failed to load accounts for provider "${provider}"; exception:`, error);
      return [];
    });
  }

  public static getAvailabilityZonesForAccountAndRegion(
    provider: string,
    account: string,
    region: string,
  ): IPromise<string[]> {
    return this.getPreferredZonesByAccount(provider).then(
      (result: IAccountZone) => (result[account] ? result[account][region] : []),
    );
  }

  public static getCredentialsKeyedByAccount(provider: string = null): IPromise<IAggregatedAccounts> {
    return this.listAllAccounts(provider).then((accounts: IAccountDetails[]) => {
      const names: string[] = accounts.map((account: IAccount) => account.name);
      return zipObject<IAccountDetails, IAggregatedAccounts>(names, accounts);
    });
  }

  public static getPreferredZonesByAccount(provider: string): IPromise<IAccountZone> {
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

  public static getRegionsForAccount(account: string): IPromise<IRegion[]> {
    return this.getAccountDetails(account).then((details: IAccountDetails) => (details ? details.regions : []));
  }

  public static getUniqueAttributeForAllAccounts(provider: string, attribute: string): IPromise<string[]> {
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

  public static listAllAccounts(provider: string = null, providerVersion: string = null): IPromise<IAccountDetails[]> {
    return $q
      .when(this.accounts$.toPromise())
      .then((accounts: IAccountDetails[]) => accounts.filter(account => !provider || account.type === provider))
      .then((accounts: IAccountDetails[]) =>
        accounts.filter(account => !providerVersion || account.providerVersion === providerVersion),
      );
  }

  public static listAccounts(provider: string = null, providerVersion: string = null): IPromise<IAccountDetails[]> {
    return this.listAllAccounts(provider, providerVersion).then(accounts =>
      accounts.filter(account => account.authorized !== false),
    );
  }

  public static applicationAccounts(application: Application = null): IPromise<IAccountDetails[]> {
    return $q.all([this.listProviders(application), this.listAccounts()]).then(([providers, accounts]) => {
      return providers.reduce(
        (memo, p) => {
          return memo.concat(accounts.filter(acc => acc.cloudProvider === p));
        },
        [] as IAccountDetails[],
      );
    });
  }

  public static listProviders$(application: Application = null): Observable<string[]> {
    return this.providers$
      .map((available: string[]) => {
        if (!application) {
          return available;
        } else if (application.attributes.cloudProviders.length) {
          return intersection(available, application.attributes.cloudProviders);
        } else if (SETTINGS.defaultProviders) {
          return intersection(available, SETTINGS.defaultProviders);
        } else {
          return available;
        }
      })
      .map(results => results.sort());
  }

  public static listProviders(application: Application = null): IPromise<string[]> {
    return $q.when(this.listProviders$(application).toPromise());
  }
}

AccountService.initialize();
