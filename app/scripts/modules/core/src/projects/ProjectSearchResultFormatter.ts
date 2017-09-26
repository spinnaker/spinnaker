import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { IResultRenderer, ISearchResult, ISearchResultFormatter, searchResultFormatterRegistry } from '../search';
import { ProjectDisplayRenderer } from './ProjectDisplayRenderer';

export interface IProjectSearchResult extends ISearchResult {
  name?: string;
  project?: string;
  config?: { applications: string[] }
}

export class ProjectSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Projects'; }
  public get order() { return 0; }
  public get icon() { return 'folder-o'; }
  public displayFormatter(searchResult: IProjectSearchResult): IPromise<string> {
    const applications = searchResult.config && searchResult.config.applications ?
      ' (' + searchResult.config.applications.join(', ') + ')' :
      '';
    const project = searchResult.name || searchResult.project;
    return $q.when(project + applications);
  }

  public get displayRenderer(): IResultRenderer {
    return ProjectDisplayRenderer.renderer()
  }
}

searchResultFormatterRegistry.register('projects', new ProjectSearchResultFormatter());
