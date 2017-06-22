import { IPromise, module } from 'angular';

import { INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService } from 'core/cache/infrastructureCaches.service';
import { API_SERVICE, Api } from 'core/api/api.service';

export interface ICertificate {
  expiration: number;
  path: string;
  serverCertificateId: string;
  serverCertificateName: string;
}

export class CertificateReader {

  public constructor(private API: Api, private infrastructureCaches: InfrastructureCacheService) { 'ngInject'; }

  public listCertificates(): IPromise<ICertificate[]> {
    return this.API.one('certificates')
      .useCache(this.infrastructureCaches.get('certificates'))
      .getList();
  }

  public listCertificatesByProvider(cloudProvider: string): IPromise<ICertificate[]> {
    return this.API.one('certificates').one(cloudProvider)
      .useCache(this.infrastructureCaches.get('certificates'))
      .getList();
  }
}

export const CERTIFICATE_READ_SERVICE = 'spinnaker.core.certificate.read.service';
module(CERTIFICATE_READ_SERVICE, [API_SERVICE, INFRASTRUCTURE_CACHE_SERVICE])
  .service('certificateReader', CertificateReader);
