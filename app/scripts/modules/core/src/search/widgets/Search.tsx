import * as React from 'react';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';

import { Key } from 'core/widgets/Keys';
import { ITag } from 'core/widgets/tags/Tag';
import { TagList } from 'core/widgets/tags/TagList';
import { IQueryParams, UrlParser } from 'core/navigation/urlParser';
import { Filters, IFiltersLayout } from './Filters';
import { IFilterType, SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';

import './search.less';

export interface ISearchProps {
  query: string;
}

export interface ISearchState {
  activeFilter: IFilterType;
  isFocused: boolean;
  isOpen: boolean;
  layouts: IFiltersLayout[];
  tags: ITag[];
}

@autoBindMethods
export class Search extends React.Component<ISearchProps, ISearchState> {

  private filterTypes: IFilterType[] = [];
  private modifiers: Set<string>;

  private tagElements: HTMLElement[] = [];
  private inputElement: HTMLInputElement;

  constructor(props: ISearchProps) {

    super(props);

    const layouts: IFiltersLayout[] = [];
    layouts.push({
      header: 'SEARCH',
      filterTypes: [SearchFilterTypeRegistry.KEYWORD_FILTER]
    });

    const types: IFilterType[] = this.reorderFilterTypesForSearch(SearchFilterTypeRegistry.getValues());
    this.filterTypes.push(...types);
    layouts.push({
      header: 'FILTER ON',
      filterTypes: types.filter((type: IFilterType) => type.modifier !== SearchFilterTypeRegistry.KEYWORD_FILTER.modifier)
    });

    this.modifiers = new Set<string>(SearchFilterTypeRegistry.getValues().map((type: IFilterType) => type.modifier));
    this.state = {
      activeFilter: SearchFilterTypeRegistry.KEYWORD_FILTER,
      isFocused: true,
      isOpen: false,
      layouts,
      tags: this.buildTagsFromQuery(props.query)
    };
  }

  private reorderFilterTypesForSearch(filterTypes: IFilterType[]): IFilterType[] {
    const mods: Set<string> =
      new Set<string>([SearchFilterTypeRegistry.KEYWORD_FILTER.modifier, SearchFilterTypeRegistry.NAME_FILTER.modifier]);
    const result: IFilterType[] = filterTypes.filter((type: IFilterType) => !mods.has(type.modifier));
    result.unshift(SearchFilterTypeRegistry.NAME_FILTER);
    result.unshift(SearchFilterTypeRegistry.KEYWORD_FILTER);

    return result;
  }

  private buildTagsFromQuery(query: string): ITag[] {

    // key in query string corresponds to IFilterType.key
    const queryParams: IQueryParams = UrlParser.parseQueryString(query);
    const result: ITag[] = [];
    Object.keys(queryParams).forEach((key: string) => {
      const filterType: IFilterType = SearchFilterTypeRegistry.getFilterType(key);
      if (filterType) {
        result.push({
          modifier: filterType.modifier,
          text: queryParams[key] as string
        });
      }
    });

    return result;
  }

  private isTagAlreadyPresent(tag: ITag): boolean {
    return this.state.tags.some((t: ITag) => t.modifier === tag.modifier && t.text === tag.text);
  }

  private hasModifier(input: string): boolean {
    const index = input.indexOf(':');
    return this.modifiers.has(input.substring(0, index).toLocaleLowerCase()) && (index !== input.length);
  }

  private getModifier(input: string): string {
    return input.substring(0, input.indexOf(':')).toLocaleLowerCase();
  }

  private getSearchText(input: string): string {
    const trimmed = input.trim();
    const searchText = trimmed.split(':')[1] || '';
    return this.hasModifier(trimmed) ? searchText.trim() : trimmed;
  }

  private buildTagFromInputString(): ITag {

    const value = this.inputElement.value.trim();
    const text = this.getSearchText(value).trim();
    if (!value || !text) {
      return null;
    }
    const modifier = this.hasModifier(value) ? this.getModifier(value) : this.state.activeFilter.modifier;

    return { modifier, text };
  }

  private getActiveFilterIndex(): number {
    return this.filterTypes.findIndex((type: IFilterType) => type === this.state.activeFilter);
  }

  private getActiveFilterText(filterType: IFilterType): string {

    let result: string;
    const text = this.inputElement.value.trim();
    if (this.hasModifier(text)) {
      const modifier = this.getModifier(text);
      const regex = new RegExp(modifier, 'i');
      result = `${text.replace(regex, filterType.modifier)}`;
    } else {
      result = `${filterType.modifier}:${text}`;
    }

    return result;
  }

  private handleTagListUpdate(elements: HTMLElement[]): void {
    this.tagElements = elements;
  }

  private refCallback(element: HTMLInputElement) {
    this.inputElement = element;
  }

  private handleBlur(): void {
    this.setState({
      isFocused: false,
      isOpen: false
    });
  }

  private handleChange(): void {

    let newState: Partial<ISearchState>;
    const value = this.inputElement.value.trim();
    if (this.hasModifier(value)) {
      const modifier = this.getModifier(value);
      newState = {
        activeFilter: this.filterTypes.find((type: IFilterType) => type.modifier === modifier),
        isOpen: true
      };
    } else {
      newState = { isOpen: !!value };
    }

    this.setState(newState as ISearchState);
  }

  private handleDelete(tag: ITag, focus: boolean): void {

    this.setState({
      isFocused: true,
      tags: this.state.tags.filter((t: ITag) => tag !== t)
    });

    if (focus) {
      this.inputElement.focus();
    }
  }

  private handleFocus(): void {
    this.setState({
      isFocused: true
    })
  }

  private navigateUp(): void {
    const { filterTypes } = this;
    const activeIndex = this.getActiveFilterIndex();
    const activeFilter = activeIndex === 0 ? filterTypes[filterTypes.length - 1] : filterTypes[activeIndex - 1];
    this.navigate(activeFilter);
  }

  private navigateDown(): void {
    const { filterTypes } = this;
    const activeIndex = this.getActiveFilterIndex();
    const activeFilter = (activeIndex === (filterTypes.length - 1)) ? filterTypes[0] : filterTypes[activeIndex + 1];
    this.navigate(activeFilter);
  }

  private navigate(active: IFilterType): void {
    const activeFilter: IFilterType = this.state.isOpen ? active : SearchFilterTypeRegistry.KEYWORD_FILTER;
    const newState: Partial<ISearchState> = { activeFilter };
    this.inputElement.value = this.getActiveFilterText(activeFilter);
    this.setState({ ...newState, isOpen: true } as ISearchState);
  }

  private handleKeyUpFromInput(event: React.KeyboardEvent<HTMLInputElement>): void {

    const length = this.tagElements.length;
    switch (event.key) {
      case Key.LEFT_ARROW:
        if (!this.inputElement.value && length >= 1) {
          this.tagElements[length - 1].focus();
        }
        break;
      case Key.RIGHT_ARROW:
        if (!this.inputElement.value && length !== 0) {
          this.tagElements[0].focus();
        }
        break;
      case Key.UP_ARROW:
        this.navigateUp();
        break;
      case Key.DOWN_ARROW:
        this.navigateDown();
        break;
      case Key.ENTER:
        const tag = this.buildTagFromInputString();
        if (tag && !this.isTagAlreadyPresent(tag)) {
          this.setState({
            activeFilter: SearchFilterTypeRegistry.KEYWORD_FILTER,
            isOpen: false,
            tags: this.state.tags.concat(tag)
          });
          this.inputElement.value = '';
        }
        break;
      case Key.ESCAPE:
        const text = this.inputElement.value;
        if (this.hasModifier(text) && !this.getSearchText(text)) {
          this.inputElement.value = '';
        }
        this.setState({
          activeFilter: SearchFilterTypeRegistry.KEYWORD_FILTER,
          isOpen: false
        });
        break;
    }
  }

  private handleKeyUpFromTag(): void {
    this.inputElement.focus();
  }

  private handleFilterChange(key: Key): void {
    switch (key) {
      case Key.UP_ARROW:
        this.navigateUp();
        break;
      case Key.DOWN_ARROW:
        this.navigateDown();
        break;
    }
  }

  private handleClearClick(): void {
    this.setState({
      isOpen: false,
      tags: []
    });
    this.inputElement.value = '';
    this.inputElement.focus();
  }

  public render(): React.ReactElement<Search> {

    const { activeFilter, isFocused, isOpen, layouts, tags } = this.state;
    const className = classNames({
      'search__input': true,
      'search__input--focus': isFocused,
      'search__input--blur': !isFocused
    });

    return (
      <div className="search">
        <i className="fa fa-search search__icon"/>
        <span className="search-label">Search</span>
        <div className={className}>
          <TagList
            tags={tags}
            onBlur={this.handleBlur}
            onDelete={this.handleDelete}
            onFocus={this.handleFocus}
            onKeyUp={this.handleKeyUpFromTag}
            onUpdate={this.handleTagListUpdate}
          />
          <div className="search__input-wrapper">
            <input
              type="text"
              ref={this.refCallback}
              autoFocus={true}
              className="search__input-control"
              placeholder="projects, applications, clusters, load balancers, server groups, security groups"
              onBlur={this.handleBlur}
              onChange={this.handleChange}
              onFocus={this.handleFocus}
              onKeyUp={this.handleKeyUpFromInput}
            />
            <i className="fa fa-times" onClick={this.handleClearClick}/>
            <Filters
              activeFilter={activeFilter}
              isOpen={isOpen}
              layouts={layouts}
              onFilterChange={this.handleFilterChange}
            />
          </div>
        </div>
      </div>
    );
  }
}
