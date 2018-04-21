import { module, IPromise } from 'angular';
import { groupBy, sortBy } from 'lodash';

import { AccountService, ICertificate, CERTIFICATE_READ_SERVICE, CertificateReader } from '@spinnaker/core';

export interface IAmazonCertificate extends ICertificate {
  arn: string;
  uploadDate: number;
}

export class AmazonCertificateReader {
  constructor(private certificateReader: CertificateReader) {
    'ngInject';
  }

  public listCertificates(): IPromise<{ [accountId: number]: IAmazonCertificate[] }> {
    return this.certificateReader.listCertificatesByProvider('aws').then((certificates: IAmazonCertificate[]) => {
      // This account grouping should really go into clouddriver but since it's not, put it here for now.
      return AccountService.getAllAccountDetailsForProvider('aws').then(allAccountDetails => {
        const accountIdToName = allAccountDetails.reduce(
          (acc, accountDetails) => {
            acc[accountDetails.accountId] = accountDetails.name;
            return acc;
          },
          {} as { [id: string]: string },
        );

        const sortedCertificates = sortBy(certificates, 'serverCertificateName');
        return groupBy(sortedCertificates, cert => {
          const [, , , , accountId] = cert.arn.split(':');
          return accountIdToName[accountId] || 'unknown';
        });
      });
    });
  }
}

export const AMAZON_CERTIFICATE_READ_SERVICE = 'spinnaker.amazon.certificate.read.service';
module(AMAZON_CERTIFICATE_READ_SERVICE, [CERTIFICATE_READ_SERVICE]).service(
  'amazonCertificateReader',
  AmazonCertificateReader,
);
