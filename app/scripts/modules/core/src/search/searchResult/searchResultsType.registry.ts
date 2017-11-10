import { ISearchResult } from 'core/search';
import { ISearchResultTabProps } from './DefaultSearchResultTab';

export interface IResultDisplayFormatter {
  (entry: ISearchResult, fromRoute?: boolean): string;
}

export type SearchResultTabComponent = React.ComponentType<ISearchResultTabProps>;
export type SearchResultsHeaderComponent = React.ComponentType<{ type: ISearchResultType }>;
export type SearchResultsDataComponent = React.ComponentType<{ type: ISearchResultType, results: any[] }>;

export interface ISearchResultType {
  /** The unique key for the type, i.e., 'applications', 'serverGroup' */
  id: string;
  order: number;
  /** search fields necessary when searching for this type, i.e., ['key'] for instance search */
  requiredSearchFields?: string[];

  /** v1 search fields */
  displayFormatter: IResultDisplayFormatter;
  hideIfEmpty?: boolean;
  iconClass: string;
  displayName: string;

  components?: {
    /** renders the tab button used to activate the search results for this ISearchResultType */
    SearchResultTab: SearchResultTabComponent;
    /** renders the faux-table column headers */
    SearchResultsHeader: SearchResultsHeaderComponent;
    /** renders the faux-table search result data rows and cells */
    SearchResultsData: SearchResultsDataComponent;
  }
}

export class SearchResultTypeRegistry {
  private types: ISearchResultType[] = [];

  public register(searchResultType: ISearchResultType): void {
    this.types.push(searchResultType);
  }

  public get(typeId: string): ISearchResultType {
    return this.types.find(f => f.id === typeId);
  }

  public getAll(): ISearchResultType[] {
    return this.types.slice().sort((a, b) => a.order - b.order);
  }

  public getSearchCategories(): string[] {
    return this.types.map(f => f.id);
  }
}

export const searchResultTypeRegistry = new SearchResultTypeRegistry();
