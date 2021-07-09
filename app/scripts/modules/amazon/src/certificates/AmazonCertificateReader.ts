import { groupBy, sortBy } from 'lodash';

import { AccountService, CertificateReader } from '@spinnaker/core';
import { IAmazonCertificate } from '../domain';

export class AmazonCertificateReader {
  public static listCertificates(): PromiseLike<{ [accountId: string]: IAmazonCertificate[] }> {
    return CertificateReader.listCertificatesByProvider('aws').then((certificates: IAmazonCertificate[]) => {
      // This account grouping should really go into clouddriver but since it's not, put it here for now.
      return AccountService.listAllAccounts('aws').then((allAccountDetails) => {
        const accountIdToName = allAccountDetails.reduce((acc, accountDetails) => {
          acc[accountDetails.accountId] = accountDetails.name;
          return acc;
        }, {} as { [id: string]: string });

        const sortedCertificates = sortBy(certificates, 'serverCertificateName');
        return groupBy(sortedCertificates, (cert) => {
          const [, , , , accountId] = cert.arn.split(':');
          return accountIdToName[accountId] || 'unknown';
        });
      });
    });
  }
}
