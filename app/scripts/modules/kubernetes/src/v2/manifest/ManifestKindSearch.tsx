import { IPromise } from 'angular';

import { SearchService } from '@spinnaker/core';

interface IManifestKindSearchResults {
  account: string;
  kubernetesKind: string;
  name: string;
  namespace: string;
  provider: string;
  region: string;
  type: string; // spinnakerKind
}

export class ManifestKindSearchService {
  public static search(kind: string, namespace: string, account: string): IPromise<IManifestKindSearchResults[]> {
    return SearchService.search<IManifestKindSearchResults>({
      q: namespace,
      type: kind,
    })
      .then(response =>
        (response ? response.results : []).filter(
          result => result.namespace === namespace && result.account === account,
        ),
      )
      .catch(() => []);
  }
}
