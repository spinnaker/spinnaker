import { get } from 'lodash';

import { ApplicationReader, IApplicationSummary } from 'core/application/service/application.read.service';
import { IClusterSearchResult } from 'core/search/searchResult/model/IClusterSearchResult';
import { ISearchResultHydrator } from 'core/search/searchResult/SearchResultHydratorRegistry';

export class ClusterSearchResultHydrator implements ISearchResultHydrator<IClusterSearchResult> {

  private static FIELDS: string[] = ['email'];

  constructor(private applicationReader: ApplicationReader) {}

  public hydrate(target: IClusterSearchResult[]): void {

    const appMap: Map<string, IApplicationSummary> = this.applicationReader.getApplicationMap();
    target.forEach((cluster: IClusterSearchResult) => {

      const app: IApplicationSummary = appMap.get(cluster.application);

      // pluck all fields from the application and set on the cluster
      const hydrationData: { [key: string]: string } =
        ClusterSearchResultHydrator.FIELDS.reduce((data: { [key: string]: string }, field: string) => {
          data[field] = get<string>(app, field);
          return data;
      }, {});
      Object.assign(cluster, hydrationData);
    });
  }
}
