export interface IFilterType {
  key: string;
  name: string;
}

export class FilterTypeRegistry {
  public KEYWORD_FILTER: IFilterType = Object.freeze({
    key: 'key',
    name: 'Keyword',
  });

  public NAME_FILTER: IFilterType = Object.freeze({
    key: 'name',
    name: 'Name',
  });

  private registry: Map<string, IFilterType> = new Map<string, IFilterType>();

  public constructor() {
    this.register(this.KEYWORD_FILTER);
    this.register(this.NAME_FILTER);
  }

  public register(filterType: IFilterType): void {
    this.registry.set(filterType.key, filterType);
  }

  public getFilterType(key: string): IFilterType {
    return this.registry.get(key);
  }

  public getFilterByModifier(key: string): IFilterType {
    return [...this.registry.values()].find((type: IFilterType) => key === type.key);
  }

  public getRegisteredFilterKeys(): string[] {
    return [...this.registry.keys()];
  }

  public getValues(): IFilterType[] {
    const filterTypes = Array.from(this.registry.values());
    filterTypes.sort((a: IFilterType, b: IFilterType) => a.name.localeCompare(b.name));

    return filterTypes;
  }
}

export const SearchFilterTypeRegistry: FilterTypeRegistry = new FilterTypeRegistry();
