import classNames from 'classnames';
import React from 'react';

import { Filter } from './Filter';
import { Filters, IFiltersLayout } from './Filters';
import { IFilterType, SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';
import { FirewallLabels } from '../../securityGroup';
import { ITag, Key, TagList } from '../../widgets';

import './search.less';

export interface ISearchProps {
  params: { [key: string]: any };
  onChange: (tags: ITag[]) => void;
}

export interface ISearchState {
  activeFilter: IFilterType;
  isFocused: boolean;
  isOpen: boolean;
  layouts: IFiltersLayout[];
  tags: ITag[];
}

export class Search extends React.Component<ISearchProps, ISearchState> {
  private filterTypes: IFilterType[] = [];
  private keys: Set<string>;

  private tagElements: HTMLElement[] = [];
  private inputElement: HTMLInputElement;

  private mouseDownFired = false;

  constructor(props: ISearchProps) {
    super(props);

    const layouts: IFiltersLayout[] = [];
    layouts.push({
      header: 'SEARCH',
      filterTypes: [SearchFilterTypeRegistry.KEYWORD_FILTER],
    });

    this.filterTypes = this.reorderFilterTypesForSearch(SearchFilterTypeRegistry.getValues());
    layouts.push({
      header: 'FILTER ON',
      filterTypes: this.filterTypes.filter((type) => type.key !== SearchFilterTypeRegistry.KEYWORD_FILTER.key),
    });

    this.keys = new Set<string>(SearchFilterTypeRegistry.getValues().map((type: IFilterType) => type.key));
    this.state = {
      activeFilter: SearchFilterTypeRegistry.KEYWORD_FILTER,
      isFocused: true,
      isOpen: false,
      layouts,
      tags: this.buildTagsFromParams(props.params, []),
    };
  }

  public componentWillReceiveProps(props: ISearchProps) {
    const tags = this.buildTagsFromParams(props.params, this.state.tags);
    this.setState({ tags });
  }

  // TODO:  ANG to clean this up by adding sort weights to filters to control order so this hackery isn't needed
  private reorderFilterTypesForSearch(filterTypes: IFilterType[]): IFilterType[] {
    const mods: Set<string> = new Set<string>([
      SearchFilterTypeRegistry.KEYWORD_FILTER.key,
      SearchFilterTypeRegistry.NAME_FILTER.key,
    ]);
    const result: IFilterType[] = filterTypes.filter((type: IFilterType) => !mods.has(type.key));
    result.unshift(SearchFilterTypeRegistry.NAME_FILTER);
    result.unshift(SearchFilterTypeRegistry.KEYWORD_FILTER);

    return result;
  }

  // Merge param changes with existing tags, maintaining the current order (new tags should go at the end)
  private buildTagsFromParams(params: { [key: string]: any }, currentTags: ITag[]): ITag[] {
    const tagsToKeep: ITag[] = currentTags
      .filter((tag) => params[tag.key])
      .map((tag) => ({ key: tag.key, text: params[tag.key] }));

    const tagsToAdd: ITag[] = Object.keys(params)
      .filter((key) => !!params[key] && !tagsToKeep.some((tag) => tag.key === key))
      .map((key) => ({ key, text: params[key] }));

    return tagsToKeep.concat(tagsToAdd);
  }

  private isLongEnoughIfKeyword(tag: ITag): boolean {
    return (
      tag.key !== SearchFilterTypeRegistry.KEYWORD_FILTER.key ||
      (tag.key === SearchFilterTypeRegistry.KEYWORD_FILTER.key && tag.text.length > 2)
    );
  }

  private hasModifier(input: string): boolean {
    const index = input.indexOf(':');
    return this.keys.has(input.substring(0, index).toLocaleLowerCase()) && index !== input.length;
  }

  private getModifier(input: string): string {
    return input.substring(0, input.indexOf(':')).toLocaleLowerCase();
  }

  private getSearchText(input: string): string {
    const trimmed = input.trim();
    const searchText = trimmed.split(':')[1] || '';
    return this.hasModifier(trimmed) ? searchText.trim() : trimmed;
  }

  private buildTagFromInputString(filter: IFilterType = this.state.activeFilter): ITag {
    const value = this.inputElement.value.trim();
    const text = this.getSearchText(value).trim();
    if (!value || !text) {
      return null;
    }
    const key = this.hasModifier(value) ? this.getModifier(value) : filter.key;

    return { key, text };
  }

  private getActiveFilterIndex(): number {
    return this.filterTypes.findIndex((type: IFilterType) => type === this.state.activeFilter);
  }

  private getActiveFilterText(filterType: IFilterType): string {
    let result: string;
    const text = this.inputElement.value.trim();
    if (this.hasModifier(text)) {
      const key = this.getModifier(text);
      const regex = new RegExp(key, 'i');
      result = `${text.replace(regex, filterType.key)}`;
    } else {
      result = `${filterType.key}:${text}`;
    }

    return result;
  }

  private handleTagListUpdate = (elements: HTMLElement[]): void => {
    this.tagElements = elements;
  };

  private refCallback = (element: HTMLInputElement): void => {
    this.inputElement = element;
  };

  private handleBlur = (): void => {
    if (!this.mouseDownFired) {
      this.setState({
        isFocused: false,
        isOpen: false,
      });
    }
  };

  private handleChange = (): void => {
    let newState: Partial<ISearchState>;
    const value = this.inputElement.value.trim();
    if (this.hasModifier(value)) {
      const key = this.getModifier(value);
      newState = {
        activeFilter: this.filterTypes.find((type: IFilterType) => type.key === key),
        isOpen: true,
      };
    } else {
      newState = { isOpen: !!value };
    }

    this.setState(newState as ISearchState);
  };

  private handleDelete = (tag: ITag, focus: boolean): void => {
    const tags = this.state.tags.filter((t: ITag) => tag !== t);
    this.setState({
      isFocused: true,
      tags,
    });
    this.props.onChange(tags);

    if (focus) {
      this.inputElement.focus();
    }
  };

  private handleFocus = (): void => {
    this.setState({
      isFocused: true,
    });
  };

  private navigateUp(): void {
    const { filterTypes } = this;
    const activeIndex = this.getActiveFilterIndex();
    const activeFilter = activeIndex === 0 ? filterTypes[filterTypes.length - 1] : filterTypes[activeIndex - 1];
    this.navigate(activeFilter);
  }

  private navigateDown(): void {
    const { filterTypes } = this;
    const activeIndex = this.getActiveFilterIndex();
    const activeFilter = activeIndex === filterTypes.length - 1 ? filterTypes[0] : filterTypes[activeIndex + 1];
    this.navigate(activeFilter);
  }

  private navigate(active: IFilterType): void {
    const activeFilter: IFilterType = this.state.isOpen ? active : SearchFilterTypeRegistry.KEYWORD_FILTER;
    const newState: Partial<ISearchState> = { activeFilter };
    this.inputElement.value = this.getActiveFilterText(activeFilter);
    this.setState({ ...newState, isOpen: true } as ISearchState);
  }

  private handleFilterSelection(filter?: IFilterType): void {
    const tag = this.buildTagFromInputString(filter);
    if (tag && this.isLongEnoughIfKeyword(tag)) {
      const tags: ITag[] = this.state.tags.filter((x) => x.key !== tag.key).concat(tag);
      this.setState({
        activeFilter: SearchFilterTypeRegistry.KEYWORD_FILTER,
        isOpen: false,
        tags,
      });
      this.inputElement.value = '';
      this.props.onChange(tags);
    }
  }

  private handleKeyUpFromInput = (event: React.KeyboardEvent<HTMLInputElement>): void => {
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
        this.handleFilterSelection();
        break;
      case Key.ESCAPE: {
        const text = this.inputElement.value;
        if (this.hasModifier(text) && !this.getSearchText(text)) {
          this.inputElement.value = '';
        }
        this.setState({
          activeFilter: SearchFilterTypeRegistry.KEYWORD_FILTER,
          isOpen: false,
        });
        break;
      }
    }
  };

  private handleKeyUpFromTag = (): void => {
    this.inputElement.focus();
  };

  private filterClicked = (filter: Filter): void => {
    this.handleFilterSelection(filter.props.filterType);
    this.mouseDownFired = false;
  };

  private handleKeyUp = (key: Key): void => {
    switch (key) {
      case Key.UP_ARROW:
        this.navigateUp();
        break;
      case Key.DOWN_ARROW:
        this.navigateDown();
        break;
    }
  };

  private handleMouseDown = (): void => {
    this.mouseDownFired = true;
  };

  private handleClearClick = (): void => {
    this.setState({
      isOpen: false,
      tags: [],
    });
    this.inputElement.value = '';
    this.inputElement.focus();
    this.props.onChange([]);
  };

  public render(): React.ReactElement<Search> {
    const { activeFilter, isFocused, isOpen, layouts, tags } = this.state;
    // prettier-ignore
    const className = classNames({
      'search__input': true,
      'search__input--focus': isFocused,
      'search__input--blur': !isFocused,
    });

    return (
      <div className="search">
        <i className="fa fa-search search__icon" />
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
              placeholder={`projects, applications, clusters, load balancers, server groups, ${FirewallLabels.get(
                'firewalls',
              )}`}
              onBlur={this.handleBlur}
              onChange={this.handleChange}
              onFocus={this.handleFocus}
              onKeyUp={this.handleKeyUpFromInput}
            />
            <i className="fa fa-times" onClick={this.handleClearClick} />
            <Filters
              activeFilter={activeFilter}
              isOpen={isOpen}
              layouts={layouts}
              filterClicked={this.filterClicked}
              onKeyUp={this.handleKeyUp}
              onMouseDown={this.handleMouseDown}
            />
          </div>
        </div>
      </div>
    );
  }
}
