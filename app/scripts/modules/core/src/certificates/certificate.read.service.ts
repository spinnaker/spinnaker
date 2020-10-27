

import { API } from 'core/api/ApiService';

export interface ICertificate {
  expiration: number;
  path: string;
  serverCertificateId: string;
  serverCertificateName: string;
  uploadDate: number;
}

export class CertificateReader {
  public static listCertificates(): PromiseLike<ICertificate[]> {
    return API.one('certificates').getList();
  }

  public static listCertificatesByProvider(cloudProvider: string): PromiseLike<ICertificate[]> {
    return API.one('certificates').one(cloudProvider).getList();
  }
}
