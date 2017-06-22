import { module, IQService, IPromise } from 'angular';
import { groupBy, sortBy } from 'lodash';

import { ACCOUNT_SERVICE, AccountService, ICertificate, CERTIFICATE_READ_SERVICE, CertificateReader } from '@spinnaker/core';

export interface IAmazonCertificate extends ICertificate {
  arn: string;
  uploadDate: number;
}

export class AmazonCertificateReader {

  private cachedAmazonCertificates: { [accountId: number]: IAmazonCertificate[] };

  constructor(private $q: IQService,
              private certificateReader: CertificateReader,
              private accountService: AccountService) {
    'ngInject';
  }

  public listCertificates(): IPromise<{ [accountId: number]: IAmazonCertificate[] }> {
    if (this.cachedAmazonCertificates) {
      return this.$q.when(this.cachedAmazonCertificates);
    }
    return this.certificateReader.listCertificatesByProvider('aws').then((certificates: IAmazonCertificate[]) => {
      // This account grouping should really go into clouddriver but since it's not, put it here for now.
      return this.accountService.getAllAccountDetailsForProvider('aws').then((allAccountDetails) => {
        const accountIdToName = allAccountDetails.reduce((acc, accountDetails) => {
          acc[accountDetails.accountId] = accountDetails.name;
          return acc;
        }, {} as {[id: string]: string});

        const sortedCertificates = sortBy(certificates, 'serverCertificateName');
        this.cachedAmazonCertificates = groupBy(sortedCertificates, (cert) => {
          const [, , , , accountId] = cert.arn.split(':');
          return accountIdToName[accountId] || 'unknown';
        });
        return this.cachedAmazonCertificates;
      });
    });
  }

  public resetCache(): void {
    this.cachedAmazonCertificates = null;
  }
}

export const AMAZON_CERTIFICATE_READ_SERVICE = 'spinnaker.amazon.certificate.read.service';
module(AMAZON_CERTIFICATE_READ_SERVICE, [
  ACCOUNT_SERVICE,
  CERTIFICATE_READ_SERVICE
]).service('amazonCertificateReader', AmazonCertificateReader);
