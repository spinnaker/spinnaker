import { IPromise } from 'angular';
import { StateService } from '@uirouter/angularjs';
import { IApplicationSearchResult } from 'core/application/applicationSearchResultType';

import { urlBuilderRegistry } from 'core/navigation/urlBuilder.registry';
import { ApplicationReader, IApplicationSummary } from './service/application.read.service';
import { IPostSearchResultSearcher } from 'core/search/searchResult/PostSearchResultSearcherRegistry';
import { ISearchResult } from 'core/search/search.service';
import { ISearchResultType, searchResultTypeRegistry } from 'core/search/searchResult/searchResultsType.registry';
import { ISearchResultSet } from 'core/search/infrastructure/infrastructureSearch.service';
import { IServerGroupSearchResult } from 'core/serverGroup/serverGroupSearchResultType';

export class ApplicationPostSearchResultSearcher implements IPostSearchResultSearcher<IServerGroupSearchResult> {
  private TYPE_ID = 'applications';

  constructor(private $state: StateService, private applicationReader: ApplicationReader) {}

  private makeSearchResult(app: IApplicationSummary): IApplicationSearchResult {
    const { TYPE_ID } = this;

    return {
      accounts: app.accounts.split(','),
      application: app.name,
      displayName: app.name,
      href: urlBuilderRegistry.getBuilder(TYPE_ID).build({
        application: app.name,
        type: TYPE_ID
      }, this.$state),
      email: app.email,
      provider: app.cloudProviders,
      type: TYPE_ID
    } as IApplicationSearchResult;
  }

  public getPostSearchResults(inputs: IServerGroupSearchResult[] = []): IPromise<ISearchResultSet[]> {
    const type: ISearchResultType = searchResultTypeRegistry.get(this.TYPE_ID);
    const appNames = inputs.map(result => result.application);

    return this.applicationReader.listApplications(true).then((apps: IApplicationSummary[]) => {
      const results: ISearchResult[] = apps
        .filter(app => appNames.includes(app.name))
        .map(app => this.makeSearchResult(app));

      return [{ type, results }];
    });
  }
}
