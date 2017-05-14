import * as React from 'react';
import { groupBy } from 'lodash';
import { Debounce } from 'lodash-decorators';
import { Subject } from 'rxjs/Subject';
import autoBindMethods from 'class-autobind-decorator';
import { Tooltip } from 'react-bootstrap';

import { Property } from '../domain/property.domain';
import { Application } from 'core/application/application.model';
import { NetflixSettings } from 'netflix/netflix.settings';

import { fastPropertyReader } from '../fastProperty.read.service';
import { FastPropertiesList } from '../view/FastPropertiesList';
import { StickyContainer } from 'core/utils/stickyHeader/StickyContainer';
import { FastPropertyFilterSearch } from '../view/filter/FastPropertyFilterSearch';
import { IFilterTag, FilterTags } from 'core/filterModel/FilterTags';
import { modalService } from 'core/modal.service';
import { $state, $stateParams } from 'core/uirouter';
import { stateEvents } from 'core/state.events';
import { Subscription } from 'rxjs/Subscription';

export interface IGroupedProperties {
  [key: string]: Property[];
}

interface IProps {
  app: Application,
}

interface IState {
  enabled: boolean;
  filters: IFilterTag[];
  filteredProperties: Property[];
  groupedProperties: IGroupedProperties;
  allProperties: Property[];
  loading?: boolean;
  groupedBy: string;
  searchTerm: string;
}

export const sortProperties = (properties: Property[]): Property[] => {
  let sortKey = $stateParams.sortBy || 'key';
  const isReversed = sortKey.startsWith('-');
  if (isReversed) {
    sortKey = sortKey.substr(1);
  }
  return properties.slice().sort((a, b) => {
    const left = isReversed ? (b[sortKey] || '') : (a[sortKey] || ''),
          right = isReversed ? (a[sortKey] || '') : (b[sortKey] || '');
    return left.localeCompare(right);
  })
};

@autoBindMethods
export class GlobalPropertiesList extends React.Component<IProps, IState> {

  private filtersUpdatedStream: Subject<IFilterTag[]> = new Subject<IFilterTag[]>();
  private stateChangeListener: Subscription;

  private searchEntered(e: React.ChangeEvent<HTMLInputElement>): void {
    const searchTerm = e.target.value;
    this.setState({searchTerm});
    this.performSearch(searchTerm);
  }

  constructor(props: IProps) {
    super(props);
    this.state = {
      enabled: NetflixSettings.feature.fastProperty,
      filters: [],
      allProperties: [],
      filteredProperties: [],
      groupedProperties: null,
      groupedBy: 'none',
      searchTerm: $stateParams.q || '',
    };
    this.filtersUpdatedStream.subscribe((newTags) => this.filtersChanged(newTags));
    if ($stateParams.q) {
      this.performSearch($stateParams.q);
    }
    this.stateChangeListener = stateEvents.stateChangeSuccess.subscribe(() => this.filtersChanged(this.state.filters));
  }

  @Debounce(300)
  private performSearch(searchTerm: string): void {
    $state.go('.', {q: searchTerm});
    this.setState({loading: true});
    fastPropertyReader.search(searchTerm).then((data) => {
      return data.map((fp) => {
        fp.appId = fp.appId || 'All (Global)';
        return fp;
      });
    }).then((searchResults) => {
      this.setState({loading: false});
      this.allPropertiesChanged(searchResults);
    }).catch(() => {
      this.setState({filteredProperties: undefined, groupedProperties: undefined, loading: false});
    });
  }


  public componentWillUnmount() {
    this.filtersUpdatedStream = null;
    this.stateChangeListener.unsubscribe();
  }

  public clearFilters(): void {
    this.filtersUpdatedStream.next([]);
  }

  private allPropertiesChanged(allProperties: Property[]): void {
    const filteredProperties = this.applyFilters(this.state.filters, this.state.allProperties);
    const groupedProperties = this.groupProperties(filteredProperties, this.state.groupedBy);
    this.setState({filters: [], filteredProperties, groupedProperties, allProperties});
  }

  private filtersChanged(newFilters: IFilterTag[]): void {
    const filteredProperties = this.applyFilters(newFilters, this.state.allProperties);
    const groupedProperties = this.groupProperties(filteredProperties, this.state.groupedBy);
    this.setState({filters: newFilters, filteredProperties, groupedProperties});
  }

  private groupedByChanged(groupedBy: string): void {
    const groupedProperties = this.groupProperties(this.state.filteredProperties, groupedBy);
    this.setState({groupedBy, groupedProperties});
  }

  private applyFilters(filters: IFilterTag[], allProperties: Property[]): Property[] {
    const groupedFilters = groupBy(filters, 'label');
    const filtered =  allProperties
      .filter(p => !filters.length || Object.keys(groupedFilters).every(k => {
        if (k === 'substring') {
          return groupedFilters[k].some(f => p.stringVal.includes(f.value));
        }
        return groupedFilters[k].some(f => p[k] === f.value || p[k] === undefined && f.value === 'none');
      }));
    return sortProperties(filtered);
  }

  private groupProperties(filteredProperties: Property[], groupedBy: string): IGroupedProperties {
    let groupedProperties: IGroupedProperties;
    if (groupedBy === 'app') {
      groupedProperties = groupBy(filteredProperties, 'appId');
    }
    if (groupedBy === 'property') {
      groupedProperties = groupBy(filteredProperties, 'key');
    }
    if (groupedProperties) {
      const sorted: IGroupedProperties = {};
      Object.keys(groupedProperties).sort().forEach(appId => {
        sorted[appId] = sortProperties(groupedProperties[appId]);
      });
      return sorted;
    }
    return groupedProperties;
  }

  private createFastProperty(): void {
    modalService.open({
      templateUrl: require('../wizard/createFastPropertyWizard.html'),
      controller: 'createFastPropertyWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Create New Fast Property',
        application: () => this.props.app
      }
    });
  }

  private groupByNone(): void {
    this.groupedByChanged('none');
  }

  private groupByApp(): void {
    this.groupedByChanged('app');
  }

  private groupByProperty(): void {
    this.groupedByChanged('property');
  }

  private createFilterButtons(): JSX.Element {
    const noneClass = this.state.groupedBy === 'none' ? 'btn btn-default active' : 'btn btn-default';
    const appClass = this.state.groupedBy === 'app' ? 'btn btn-default active' : 'btn btn-default';
    const propertyClass = this.state.groupedBy === 'property' ? 'btn btn-default active' : 'btn btn-default';
    return (
      <div className="form-group">
        <label htmlFor="propertyGroup">Group </label>
        <div className="btn-group" role="group" id="propertyGroup">
          <button className={noneClass} onClick={this.groupByNone}>None</button>
          <button className={appClass} onClick={this.groupByApp}>App</button>
          <button className={propertyClass} onClick={this.groupByProperty}>Property</button>
        </div>
      </div>
    );
  }

  public render() {
    const application = this.props.app;
    const {filters, allProperties, filteredProperties, groupedProperties, groupedBy} = this.state;
    const showAllControls = !this.state.loading && this.state.searchTerm;
    return (
      <div className="flex-fill">
        <div className="fast-properties flex-fill">
          <div className="form form-inline header">
            <div className="form-group">
              <label className="no-left-padding" htmlFor="propertySearch">Search </label>
              <input
                type="search"
                id="propertySearch"
                placeholder="Search properties"
                className="form-control"
                autoFocus={true}
                onChange={this.searchEntered}
                value={this.state.searchTerm}
              />
              {showAllControls && this.createFilterButtons()}
              {showAllControls && (
                <FastPropertyFilterSearch
                  properties={this.state.allProperties}
                  filtersUpdatedStream={this.filtersUpdatedStream}
                />
              )}
            </div>
            <div className="form-group pull-right">
              <button className="btn btn-sm btn-default" onClick={this.createFastProperty} style={{margin: '3px'}}>
                <span className="glyphicon glyphicon-plus-sign visible-lg-inline"/>
                <Tooltip value="Create Fast Property" id="createFastProperty">
                  <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline"/>
                </Tooltip>
                <span className="visible-lg-inline"> Create Fast Property</span>
              </button>
            </div>

            <div className="fast-property-filter-tags">
              <FilterTags tags={filters} clearFilters={this.clearFilters}/>
            </div>
          </div>
          {this.state.loading && (
            <h3 className="text-center"><span className="fa fa-cog fa-spin"/></h3>
          )}
          {!this.state.searchTerm && (
            <h3 className="text-center">Enter a search term to begin</h3>
          )}
          {showAllControls && (
            <StickyContainer className="flex-fill properties-container">
              <FastPropertiesList
                groupedBy={groupedBy}
                application={application}
                allProperties={allProperties}
                filteredProperties={filteredProperties}
                groupedProperties={groupedProperties}
              />
            </StickyContainer>
          )}
        </div>
      </div>
    );
  }
}
