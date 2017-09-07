import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { IResultRenderer, ISearchResult, ISearchResultFormatter, searchResultFormatterRegistry } from '../search';
import { SecurityGroupDisplayRenderer } from './SecurityGroupDisplayRenderer';

export interface ISecurityGroupSearchResult extends ISearchResult {
  name: string;
  region: string;
  application: string;
}

export class SecurityGroupSearchResultFormatter implements ISearchResultFormatter {

  public get displayName() { return 'Security Groups'; }
  public get order() { return 6; }
  public get icon() { return 'exchange'; }
  public displayFormatter(searchResult: ISecurityGroupSearchResult): IPromise<string> {
    return $q.when(searchResult.name + ' (' + searchResult.region + ')');
  }
  public get displayRenderer(): IResultRenderer {
    return SecurityGroupDisplayRenderer.renderer()
  }
}

searchResultFormatterRegistry.register('securityGroups', new SecurityGroupSearchResultFormatter());
