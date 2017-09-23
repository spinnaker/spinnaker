import { get } from 'lodash';

import { ApplicationReader, IApplicationSummary } from 'core/application/service/application.read.service';
import { IServerGroupSearchResult } from 'core/search/searchResult/model/IServerGroupSearchResult';
import { ISearchResultHydrator } from 'core/search/searchResult/SearchResultHydratorRegistry';

export class ServerGroupSearchResultHydrator implements ISearchResultHydrator<IServerGroupSearchResult> {

  private static FIELDS: string[] = ['email'];

  constructor(private applicationReader: ApplicationReader) {}

  public hydrate(target: IServerGroupSearchResult[]): void {

    const appMap: Map<string, IApplicationSummary> = this.applicationReader.getApplicationMap();
    target.forEach((serverGroup: IServerGroupSearchResult) => {

      const app: IApplicationSummary = appMap.get(serverGroup.application);

      // pluck all fields from the application and set on the server group
      const hydrationData: { [key: string]: string } =
        ServerGroupSearchResultHydrator.FIELDS.reduce((data: { [key: string]: string }, field: string) => {
          data[field] = get<string>(app, field);
          return data;
        }, {});
      Object.assign(serverGroup, hydrationData);
    });
  }
}
