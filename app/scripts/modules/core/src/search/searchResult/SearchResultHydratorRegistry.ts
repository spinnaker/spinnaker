import { ISearchResult } from '../search.service';

export interface ISearchResultHydrator<T extends ISearchResult> {
  hydrate: (target: T[]) => void;
}

export class ResultHydratorRegistry {

  private registry: Map<string, ISearchResultHydrator<ISearchResult>> =
    new Map<string, ISearchResultHydrator<ISearchResult>>();

  public register<T extends ISearchResult>(type: string, hydrator: ISearchResultHydrator<T>): void {
    this.registry.set(type, hydrator);
  }

  public getSearchResultHydrator<T extends ISearchResult>(type: string): ISearchResultHydrator<T> {
    return this.registry.get(type);
  }

  public getHydratorKeys(): string[] {
    return [...this.registry.keys()];
  }
}

export const SearchResultHydratorRegistry: ResultHydratorRegistry = new ResultHydratorRegistry();
