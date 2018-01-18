import { IPromise } from 'angular';

import { ISearchResultSet } from '../infrastructure/infrastructureSearch.service';

export interface IPostSearchResultSearcher {
  getPostSearchResults: (sourceData: ISearchResultSet) => IPromise<ISearchResultSet>
}

export interface ITypeMapping {
  sourceType: string;
  targetType: string;
}

export class SearchResultSearcherRegistry {
  private searcherRegistry: Map<string, IPostSearchResultSearcher> = new Map<string, IPostSearchResultSearcher>();
  private typeMappingRegistry: Map<string, string> = new Map<string, string>();

  public register(sourceType: string,
                  targetType: string,
                  searcher: IPostSearchResultSearcher): void {
    this.searcherRegistry.set(sourceType, searcher);
    this.typeMappingRegistry.set(sourceType, targetType);
  }

  public getPostResultSearcher(type: string): IPostSearchResultSearcher {
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
