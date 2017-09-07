export interface IFilterType {
  key: string;
  modifier: string;
  text: string;
}

export class FilterTypeRegistry {

  public KEYWORD_FILTER: IFilterType = Object.freeze({
    key: 'key',
    modifier: 'key',
    text: 'Keyword'
  });

  public NAME_FILTER: IFilterType = Object.freeze({
    key: 'name',
    modifier: 'name',
    text: 'Name'
  });

  private registry: Map<string, IFilterType> = new Map<string, IFilterType>();

  public constructor() {
    this.register(this.KEYWORD_FILTER);
    this.register(this.NAME_FILTER);
  }

  public register(filterType: IFilterType): void {
    const { key, modifier, text } = filterType;
    this.registry.set(filterType.key, {
      key, text, modifier: modifier.toLocaleLowerCase()
    });
  }

  public getFilterType(key: string): IFilterType {
    return this.registry.get(key);
  }

  public getFilterByModifier(modifier: string): IFilterType {
    return [...this.registry.values()].find((type: IFilterType) => modifier === type.modifier);
  }

  public getValues(): IFilterType[] {
    const filterTypes = Array.from(this.registry.values());
    filterTypes.sort((a: IFilterType, b: IFilterType) => a.text.localeCompare(b.text));

    return filterTypes;
  }
}

export const SearchFilterTypeRegistry: FilterTypeRegistry = new FilterTypeRegistry();
