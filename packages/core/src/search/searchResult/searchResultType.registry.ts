import { SearchResultType } from './searchResultType';

export class SearchResultTypeRegistry {
  private types: SearchResultType[] = [];

  public register(searchResultType: SearchResultType): void {
    this.types.push(searchResultType);
  }

  public get(typeId: string): SearchResultType {
    return this.types.find((f) => f.id === typeId);
  }

  public getAll(): SearchResultType[] {
    return this.types.slice().sort((a, b) => a.order - b.order);
  }

  public getSearchCategories(): string[] {
    return this.types.map((f) => f.id);
  }
}

export const searchResultTypeRegistry = new SearchResultTypeRegistry();
