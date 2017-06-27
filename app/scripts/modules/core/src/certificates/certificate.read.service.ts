import { IPromise, module } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';

export interface ICertificate {
  expiration: number;
  path: string;
  serverCertificateId: string;
  serverCertificateName: string;
}

export class CertificateReader {

  public constructor(private API: Api) { 'ngInject'; }

  public listCertificates(): IPromise<ICertificate[]> {
    return this.API.one('certificates')
      .getList();
  }

  public listCertificatesByProvider(cloudProvider: string): IPromise<ICertificate[]> {
    return this.API.one('certificates').one(cloudProvider)
      .getList();
  }
}

export const CERTIFICATE_READ_SERVICE = 'spinnaker.core.certificate.read.service';
module(CERTIFICATE_READ_SERVICE, [API_SERVICE])
  .service('certificateReader', CertificateReader);
