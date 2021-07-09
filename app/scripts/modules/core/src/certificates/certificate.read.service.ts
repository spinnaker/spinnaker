import { REST } from '../api/ApiService';

export interface ICertificate {
  expiration: number;
  path: string;
  serverCertificateId: string;
  serverCertificateName: string;
  uploadDate: number;
}

export class CertificateReader {
  public static listCertificates(): PromiseLike<ICertificate[]> {
    return REST('/certificates').get();
  }

  public static listCertificatesByProvider(cloudProvider: string): PromiseLike<ICertificate[]> {
    return REST('/certificates').path(cloudProvider).get();
  }
}
