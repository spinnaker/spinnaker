import { REST } from 'core/api/ApiService';

export interface ICertificate {
  expiration: number;
  path: string;
  serverCertificateId: string;
  serverCertificateName: string;
  uploadDate: number;
}

export class CertificateReader {
  public static listCertificates(): PromiseLike<ICertificate[]> {
    return REST().path('certificates').get();
  }

  public static listCertificatesByProvider(cloudProvider: string): PromiseLike<ICertificate[]> {
    return REST().path('certificates', cloudProvider).get();
  }
}
