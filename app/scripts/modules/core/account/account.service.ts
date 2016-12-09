import {chain, intersection, zipObject} from 'lodash';
import {module} from 'angular';

import {Application} from 'core/application/application.model';
import {API_SERVICE, Api} from 'core/api/api.service';

export interface IRegion {
  account?: string;
  availabilityZones?: string[];
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

export interface IAccountDetails extends IAccount {
  accountType: string;
  awsAccount?: string;
  awsVpc?: string;
  challengeDestructiveActions: boolean;
  cloudProvider: string;
  environment: string;
  primaryAccount: boolean;
  regions: IRegion[];
}

export interface IAggregatedAccounts {
  [key: string]: IAccountDetails;
}

export interface IZone {
  [key: string]: string[];
}

export interface IAccountZone {
  [key: string]: IZone;
}

export class AccountService {

  static get $inject() {
    return ['$log', '$q', 'settings', 'cloudProviderRegistry', 'API'];
  }

  constructor(private $log: ng.ILogService,
              private $q: ng.IQService,
              private settings: any,
              private cloudProviderRegistry: any,
              private API: Api) {
  }

  public challengeDestructiveActions(account: string): ng.IPromise<boolean> {
    return this.$q((resolve: ng.IQResolveReject<boolean>) => {
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

  public getAccountDetails(account: string): ng.IPromise<IAccountDetails> {
    return this.API.one('credentials')
      .one(account)
      .useCache()
      .get();
  }

  public getAllAccountDetailsForProvider(provider: string): ng.IPromise<IAccountDetails[]> {
    return this.listAccounts(provider)
      .then((accounts: IAccount[]) => this.$q.all(accounts.map((account: IAccount) => this.getAccountDetails(account.name))))
      .catch((error: any) => {
        this.$log.warn(`Failed to load accounts for provider "${provider}"; exception:`, error);
        return [];
      });
  }

  public getAvailabilityZonesForAccountAndRegion(provider: string, account: string, region: string): ng.IPromise<string[]> {
    return this.getPreferredZonesByAccount(provider)
      .then((result: IAccountZone) => result[account] ? result[account][region] : []);
  }

  public getCredentialsKeyedByAccount(provider: string = null): ng.IPromise<IAggregatedAccounts> {
    return this.listAccounts(provider)
      .then((accounts: IAccount[]) => {
        const requests: ng.IPromise<IAccountDetails>[] =
          accounts.map((account: IAccount) => this.getAccountDetails(account.name));
        const names: string[] = accounts.map((account: IAccount) => account.name);
        return this.$q.all(requests)
          .then((credentials: IAccountDetails[]): IAggregatedAccounts => zipObject<IAccountDetails, IAggregatedAccounts>(names, credentials));
      });
  }

  public getPreferredZonesByAccount(provider: string): ng.IPromise<IAccountZone> {
    const preferred: IAccountZone = {};
    return this.getAllAccountDetailsForProvider(provider)
      .then((accounts: IAccountDetails[]) => {
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

  public getRegionsForAccount(account: string): ng.IPromise<IRegion[]> {
    return this.getAccountDetails(account)
      .then((details: IAccountDetails) => details ? details.regions : []);
  }

  public getUniqueAttributeForAllAccounts(provider: string, attribute: string): ng.IPromise<string[]> {
    return this.getCredentialsKeyedByAccount(provider)
      .then((credentials: IAggregatedAccounts) => {
        return chain(credentials)
          .map(attribute)
          .flatten().compact()
          .map((region: IRegion) => region.name || region)
          .uniq()
          .value();
      });
  }

  public getUniqueGceZonesForAllAccounts(provider: string): ng.IPromise<IZone> {
    return this.getCredentialsKeyedByAccount(provider)
      .then((regions: IAggregatedAccounts) => {
        return chain(regions)
          .map('regions')
          .flatten()
          .reduce((zone: IZone, object: IZone) => {
            Object.keys(object).forEach((key: string) => {
              if (zone[key]) {
                zone[key] = Array.from(new Set([...zone[key], ...object[key]]));
              } else {
                zone[key] = object[key];
              }
            });

            return zone;
          }, {})
          .value();
      });
  }

  public listAccounts(provider: string = null): ng.IPromise<IAccount[]> {

    let result: ng.IPromise<IAccount[]> = this.API.one('credentials').useCache().get();
    if (provider) {
      result = result.then((accounts: IAccount[]) => accounts.filter((account: IAccount) => account.type === provider));
    }

    return result;
  }

  public listProviders(application: Application = null): ng.IPromise<string[]> {
    return this.listAccounts()
      .then((accounts: IAccount[]) => {
        const all: string[] = Array.from(new Set(accounts.map((account: IAccount) => account.type)));
        const available: string[] = intersection(all, this.cloudProviderRegistry.listRegisteredProviders());
        let result: string[];
        if (application) {
          if (application.attributes.cloudProviders.length) {
            result = application.attributes.cloudProviders;
          } else {
            if (this.settings.defaultProviders) {
              result = this.settings.defaultProviders;
            } else {
              result = available;
            }
          }

          result = intersection(available, result);
        } else {
          result = available;
        }

        return result.sort();
      });
  }
}

export const ACCOUNT_SERVICE = 'spinnaker.core.account.service';
module(ACCOUNT_SERVICE, [
  require('core/config/settings'),
  require('core/cloudProvider/cloudProvider.registry'),
  API_SERVICE
])
  .service('accountService', AccountService);
