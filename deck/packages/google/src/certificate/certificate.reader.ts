import type { ISearchResults } from '@spinnaker/core';
import { InfrastructureCaches, SearchService } from '@spinnaker/core';

export interface IGceCertificate {
  account: string;
  name: string;
  provider: string;
  type: string;
}

export class GceCertificateReader {
  public listCertificates(): Promise<IGceCertificate[]> {
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
