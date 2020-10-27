import { module } from 'angular';

import { InfrastructureCaches, ISearchResults, SearchService } from '@spinnaker/core';

export interface IGceCertificate {
  account: string;
  name: string;
  provider: string;
  type: string;
}

export class GceCertificateReader {
  public listCertificates(): PromiseLike<IGceCertificate[]> {
    return SearchService.search<IGceCertificate>(
      { q: '', type: 'sslCertificates', allowShortQuery: 'true' },
      InfrastructureCaches.get('certificates'),
    )
      .then((searchResults: ISearchResults<IGceCertificate>) => {
        if (searchResults && searchResults.results) {
          return searchResults.results.filter((certificate) => certificate.provider === 'gce');
        } else {
          return [];
        }
      })
      .catch(() => []);
  }
}

export const GCE_CERTIFICATE_READER = 'spinnaker.gce.certificateReader.service';
module(GCE_CERTIFICATE_READER, []).service('gceCertificateReader', GceCertificateReader);
