import { IPromise } from 'angular';
import { ISearchResult } from '../search.service';
export interface ISearchResultFormatter {
  displayName: string;
  order: number;
  displayFormatter: IResultDisplayFormatter;
  icon?: string;
  iconClass?: string;
  hideIfEmpty?: boolean;
}

export interface IResultDisplayFormatter {
  (entry: ISearchResult, fromRoute?: boolean): IPromise<string>;
}

export class SearchResultFormatterRegistry {
  private formatters: {[key: string]: ISearchResultFormatter} = {};

  public register(type: string, formatter: ISearchResultFormatter): void {
    this.formatters[type] = formatter;
  }

  public get(type: string): ISearchResultFormatter {
    return this.formatters[type];
  }

  public getSearchCategories(): string[] {
    return Object.keys(this.formatters);
  }
}

export const searchResultFormatterRegistry = new SearchResultFormatterRegistry();
