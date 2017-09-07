import { IPromise } from 'angular';
import { ISearchResult } from '../search.service';

export interface IResultDisplayFormatter {
  (entry: ISearchResult, fromRoute?: boolean): IPromise<string>;
}

export interface IResultRenderer {
  scrollToTop: () => void;
  render: (items: any[]) => JSX.Element;
}

export interface ISearchResultFormatter {
  displayName: string;
  order: number;
  displayFormatter: IResultDisplayFormatter;
  displayRenderer?: IResultRenderer;
  icon?: string;
  iconClass?: string;
  hideIfEmpty?: boolean;
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
