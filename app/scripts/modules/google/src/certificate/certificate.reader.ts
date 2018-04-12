import { IPromise, module } from 'angular';

import { InfrastructureCaches, ISearchResults, SEARCH_SERVICE, SearchService } from '@spinnaker/core';

export interface IGceCertificate {
  account: string;
  name: string;
  provider: string;
  type: string;
}

export class GceCertificateReader {
  constructor(private searchService: SearchService) {
    'ngInject';
  }

  public listCertificates(): IPromise<IGceCertificate[]> {
    return this.searchService
      .search<IGceCertificate>(
        { q: '', type: 'sslCertificates', allowShortQuery: 'true' },
        InfrastructureCaches.get('certificates'),
      )
      .then((searchResults: ISearchResults<IGceCertificate>) => {
        if (searchResults && searchResults.results) {
          return searchResults.results.filter(certificate => certificate.provider === 'gce');
        } else {
          return [];
        }
      })
      .catch(() => []);
  }
}

export const GCE_CERTIFICATE_READER = 'spinnaker.gce.certificateReader.service';
module(GCE_CERTIFICATE_READER, [SEARCH_SERVICE]).service('gceCertificateReader', GceCertificateReader);
