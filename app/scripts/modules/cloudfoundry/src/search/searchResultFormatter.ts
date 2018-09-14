import { module } from 'angular';

export interface ICloudFoundryServerGroupSearchResult {
  serverGroup: string;
  region: string;
}

export class CloudFoundrySearchFormatter {
  public static serverGroups(serverGroup: ICloudFoundryServerGroupSearchResult) {
    const [org, space] = serverGroup.region.split('_');
    return `${serverGroup.serverGroup} (${org}/${space})`;
  }
}

export const CLOUD_FOUNDRY_SEARCH_FORMATTER = 'spinnaker.cloudfoundry.search.searchResultFormatter';

module(CLOUD_FOUNDRY_SEARCH_FORMATTER, []).service('cfSearchResultFormatter', CloudFoundrySearchFormatter);
