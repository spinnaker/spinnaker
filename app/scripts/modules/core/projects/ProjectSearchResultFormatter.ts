import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { ISearchResultFormatter } from '../search/searchResult/searchResultFormatter.registry';
import { ISearchResult } from '../search/search.service';
import { searchResultFormatterRegistry } from '../search/searchResult/searchResultFormatter.registry';

export interface IProjectSearchResult extends ISearchResult {
  name?: string;
  project?: string;
  config?: { applications: string[] }
}

export class ProjectSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Projects'; }
  public get order() { return 0; }
  public displayFormatter(searchResult: IProjectSearchResult): IPromise<string> {
    const applications = searchResult.config && searchResult.config.applications ?
      ' (' + searchResult.config.applications.join(', ') + ')' :
      '';
    const project = searchResult.name || searchResult.project;
    return $q.when(project + applications);
  }
}

searchResultFormatterRegistry.register('projects', new ProjectSearchResultFormatter());
