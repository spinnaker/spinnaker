import { IPromise } from 'angular';
import { StateService } from '@uirouter/angularjs';

import { urlBuilderRegistry } from 'core/navigation/urlBuilder.registry';
import { ApplicationReader, IApplicationSummary } from './service/application.read.service';
import { IPostSearchResultSearcher } from 'core/search/searchResult/PostSearchResultSearcherRegistry';
import { ISearchResult } from 'core/search/search.service';
import { ISearchResultFormatter, searchResultFormatterRegistry } from 'core/search/searchResult/searchResultFormatter.registry';
import { ISearchResultSet } from 'core/search/infrastructure/infrastructureSearch.service';
import { IServerGroupSearchResult } from 'core/search/searchResult/model/IServerGroupSearchResult';

export class ApplicationPostSearchResultSearcher implements IPostSearchResultSearcher<IServerGroupSearchResult> {

  private static TYPE = 'applications';

  constructor(private $state: StateService, private applicationReader: ApplicationReader) {}

  public getPostSearchResults(inputs: IServerGroupSearchResult[] = []): IPromise<ISearchResultSet[]> {

    const names: Set<string> = new Set<string>(inputs.map((result: IServerGroupSearchResult) => result.application));
    return this.applicationReader.listApplications(true).then((apps: IApplicationSummary[]) => {

      const results: ISearchResult[] = apps.filter((app: IApplicationSummary) => names.has(app.name))
        .map((app: IApplicationSummary) => {
          return {
            accounts: app.accounts,
            application: app.name,
            displayName: app.name,
            href: urlBuilderRegistry.getBuilder(ApplicationPostSearchResultSearcher.TYPE).build({
              application: app.name,
              type: ApplicationPostSearchResultSearcher.TYPE
            }, this.$state),
            email: app.email,
            provider: app.cloudProviders,
            type: ApplicationPostSearchResultSearcher.TYPE
          };
        });
      const formatter: ISearchResultFormatter = searchResultFormatterRegistry.get(ApplicationPostSearchResultSearcher.TYPE);

      return [{
        id: ApplicationPostSearchResultSearcher.TYPE,
        category: ApplicationPostSearchResultSearcher.TYPE,
        icon: formatter.icon,
        iconClass: '',
        order: formatter.order,
        results
      }];
    });
  }
}
