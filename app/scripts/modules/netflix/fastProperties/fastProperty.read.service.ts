import { module, IPromise } from 'angular';
import {API_SERVICE, Api} from 'core/api/api.service';
import {Scope} from './domain/scope.domain';

export class FastPropertyReaderService {
  constructor(private API: Api) { 'ngInject'; }

  public fetchForAppName(appName: string): IPromise<any> {
    return this.API.all('fastproperties').all('application').one(appName).get();
  }

  public search(searchTerm: string): IPromise<any> {
    return this.API.all('fastproperties').all('search').one(searchTerm).get();
  }

  public getPropByIdAndEnv(id: string, env: string): IPromise<any> {
    return this.API.all('fastproperties').one('id', id).one('env', env).get();
  }

  public fetchImpactCountForScope(fastPropertyScope: Scope): IPromise<any> {
    return this.API.all('fastproperties').all('impact').post(fastPropertyScope);
  }

}

export const FAST_PROPERTY_READ_SERVICE = 'spinnaker.netflix.fastProperties.read.service';

module(FAST_PROPERTY_READ_SERVICE, [
    API_SERVICE,
  ])
  .service('fastPropertyReader', FastPropertyReaderService);
