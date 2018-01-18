import { IPromise } from 'angular';
import { StateService } from '@uirouter/angularjs';

import { IApplicationSearchResult } from 'core/application/applicationSearchResultType';
import { IServerGroupSearchResult } from 'core/serverGroup/serverGroupSearchResultType';
import { urlBuilderRegistry } from 'core/navigation/urlBuilder.registry';
import {
  IPostSearchResultSearcher, ISearchResult, ISearchResultSet, ISearchResultType, searchResultTypeRegistry
} from 'core/search';

import { ApplicationReader, IApplicationSummary } from './service/application.read.service';

export class ApplicationPostSearchResultSearcher implements IPostSearchResultSearcher {
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
      type: TYPE_ID,
    } as IApplicationSearchResult;
  }

  public getPostSearchResults(resultSet: ISearchResultSet<IServerGroupSearchResult>): IPromise<ISearchResultSet> {
    const serverGroups = resultSet.results;
    const appNames = serverGroups.map(result => result.application);
    const type: ISearchResultType = searchResultTypeRegistry.get(this.TYPE_ID);

    return this.applicationReader.listApplications(true).then((apps: IApplicationSummary[]) => {
      const results: ISearchResult[] = apps
        .filter(app => appNames.includes(app.name))
        .map(app => this.makeSearchResult(app));

      const { status, error } = resultSet;
      return { status, error, type, results };
    });
  }
}
