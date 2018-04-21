import { IPromise, module } from 'angular';

import { API } from 'core/api/ApiService';

export interface ICertificate {
  expiration: number;
  path: string;
  serverCertificateId: string;
  serverCertificateName: string;
}

export class CertificateReader {
  public listCertificates(): IPromise<ICertificate[]> {
    return API.one('certificates').getList();
  }

  public listCertificatesByProvider(cloudProvider: string): IPromise<ICertificate[]> {
    return API.one('certificates')
      .one(cloudProvider)
      .getList();
  }
}

export const CERTIFICATE_READ_SERVICE = 'spinnaker.core.certificate.read.service';
module(CERTIFICATE_READ_SERVICE, []).service('certificateReader', CertificateReader);
