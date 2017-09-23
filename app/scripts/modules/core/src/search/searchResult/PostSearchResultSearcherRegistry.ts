import { IPromise } from 'angular';

import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';
import { ISearchResult } from '../search.service';

export interface IPostSearchResultSearcher<T extends ISearchResult> {
  getPostSearchResults: (inputs: T[]) => IPromise<ISearchResultSet[]>
}

export interface ITypeMapping {
  sourceType: string;
  targetType: string;
}

export class SearchResultSearcherRegistry {

  private searcherRegistry: Map<string, IPostSearchResultSearcher<ISearchResult>> =
    new Map<string, IPostSearchResultSearcher<ISearchResult>>();

  private typeMappingRegistry: Map<string, string> = new Map<string, string>();

  public register<T extends ISearchResult>(sourceType: string,
                                           targetType: string,
                                           searcher: IPostSearchResultSearcher<T>): void {
    this.searcherRegistry.set(sourceType, searcher);
    this.typeMappingRegistry.set(sourceType, targetType);
  }

  public getPostResultSearcher<T extends ISearchResult>(type: string): IPostSearchResultSearcher<T> {
    return this.searcherRegistry.get(type);
  }

  public getRegisteredTypes(): ITypeMapping[] {
    return [...this.typeMappingRegistry.entries()].map((entry: string[]) => {
      return {
        sourceType: entry[0],
        targetType: entry[1]
      };
    });
  }
}

export const PostSearchResultSearcherRegistry: SearchResultSearcherRegistry = new SearchResultSearcherRegistry();
