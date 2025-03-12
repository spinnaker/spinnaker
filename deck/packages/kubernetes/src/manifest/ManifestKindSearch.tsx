import { chain } from 'lodash';

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
  public static search(kind: string, namespace: string, account: string): PromiseLike<IManifestKindSearchResults[]> {
    return SearchService.search<IManifestKindSearchResults>({
      q: namespace,
      type: kind,
    })
      .then((response) =>
        chain(response ? response.results : [])
          .filter(
            (result) => result.namespace === namespace && result.account === account && result.kubernetesKind === kind,
          )
          .uniqBy('name')
          .valueOf(),
      )
      .catch(() => []);
  }
}
