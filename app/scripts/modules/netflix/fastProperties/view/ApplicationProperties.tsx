import autoBindMethods from 'class-autobind-decorator';
import { groupBy } from 'lodash';
import * as React from 'react';
import { Subject } from 'rxjs/Subject';
import { Subscription } from 'rxjs/Subscription';

import { Application, ApplicationDataSource, FilterTags, IFilterTag, ReactInjector } from '@spinnaker/core';

import { NetflixSettings } from 'netflix/netflix.settings';
import { NetflixReactInjector } from 'netflix/react.injector';
import { Property } from '../domain/property.domain';
import { sortProperties } from '../global/GlobalPropertiesList';
import { FastPropertiesList } from './FastPropertiesList';
import { FastPropertyRollouts } from './rollouts/FastPropertyRollouts';

interface IProps {
  application: Application;
}

interface IState {
  enabled: boolean;
  loading: boolean;
  loadError?: boolean;
  filters: IFilterTag[];
  filteredProperties: Property[];
  allProperties?: Property[];
  runningPromotionsCount: number;
  sortBy: string;
  activeSection: string;
}

@autoBindMethods
export class ApplicationProperties extends React.Component<IProps, IState> {

  private filtersUpdatedStream: Subject<IFilterTag[]> = new Subject<IFilterTag[]>();
  private dataSourceUnsubscribe: () => any;
  private dataSource: ApplicationDataSource;
  private runningDataSource: ApplicationDataSource;
  private runningDataSourceUnsubscribe: () => any;
  private stateChangeListener: Subscription;

  constructor(props: IProps) {
    super(props);
    const { $stateParams } = ReactInjector;

    this.runningDataSource = this.props.application.getDataSource('runningPropertyPromotions');

    this.state = {
      enabled: NetflixSettings.feature.fastProperty,
      loading: true,
      filters: [],
      filteredProperties: [],
      allProperties: [],
      sortBy: $stateParams.sortBy || 'key',
      activeSection: $stateParams.tab || 'properties',
      runningPromotionsCount: this.runningDataSource.data.length,
    };
    this.dataSource = this.props.application.getDataSource('properties');
  }

  public componentDidMount() {
    this.dataSource.activate();
    this.dataSource.ready().then(() => this.dataUpdated());
    this.dataSourceUnsubscribe = this.dataSource.onRefresh(null,
      () => this.dataUpdated(),
      () => this.dataLoadError(),
    );

    this.runningDataSourceUnsubscribe = this.runningDataSource.onRefresh(null,
      () => this.setState({runningPromotionsCount: this.runningDataSource.data.length})
    );
    this.filtersUpdatedStream.subscribe((newTags) => this.applyFilters(newTags));
    this.stateChangeListener = ReactInjector.stateEvents.stateChangeSuccess.subscribe(
      () => {
        const activeSection = ReactInjector.$stateParams.tab || 'properties';
        this.setState({activeSection});
        this.applyFilters(this.state.filters);
      }
    );
  }

  public componentWillUnmount() {
    this.filtersUpdatedStream = null;
    this.stateChangeListener.unsubscribe();
    this.dataSourceUnsubscribe();
    this.runningDataSourceUnsubscribe();
    this.dataSource.deactivate();
  }

  public clearFilters(): void {
    this.applyFilters([]);
    this.filtersUpdatedStream.next();
  }

  private dataUpdated(): void {
    this.setState({loading: false, allProperties: this.dataSource.data});
    this.applyFilters();
  }

  private dataLoadError(): void {
    this.setState({loading: false, loadError: true});
  }

  private applyFilters(filters: IFilterTag[] = this.state.filters): void {
    const groupedFilters = groupBy(filters, 'label');
    const filteredProperties = this.dataSource.data
      .filter(p => !filters.length || Object.keys(groupedFilters).every(k => {
        if (k === 'substring') {
          return groupedFilters[k].some(f => p.stringVal.includes(f.value));
        }
        return groupedFilters[k].some(f => p[k] === f.value);
      }));
    const sorted = sortProperties(filteredProperties);
    this.setState({filteredProperties: sorted, filters});
  }

  private createFastProperty(): void {
    ReactInjector.modalService.open({
      templateUrl: require('../wizard/createFastPropertyWizard.html'),
      controller:  'createFastPropertyWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Create New Fast Property',
        application: () => this.props.application
      }
    });
  }

  private handleSelect(event: React.MouseEvent<HTMLElement>): void {
    const activeSection = (event.currentTarget as HTMLElement).getAttribute('data-section');
    ReactInjector.$state.go('.', {tab: activeSection});
  }

  public render() {
    if (this.state.loading) {
      return <h3 className="text-center"><span className="fa fa-cog fa-spin"/></h3>;
    }

    const { application } = this.props;
    const { filters, allProperties, filteredProperties } = this.state;

    const runningCount = this.state.runningPromotionsCount;

    return (
      <div className="fast-properties flex-fill">
        <div className="form form-inline header">
          <div className="form-group">
            <h3>Properties</h3>
            <NetflixReactInjector.FastPropertyFilterSearch properties={this.state.allProperties} filtersUpdatedStream={this.filtersUpdatedStream}/>
          </div>
          <div className="form-group pull-right">
            <button className="btn btn-sm btn-default" onClick={this.createFastProperty} style={{margin: '3px'}}>
              <span className="glyphicon glyphicon-plus-sign"/>
              <span> Create Fast Property</span>
            </button>
          </div>

          <div className="fast-property-filter-tags">
            <FilterTags tags={filters} clearFilters={this.clearFilters}/>
          </div>
        </div>
        <ul className="nav nav-tabs clickable">
          <li className={this.state.activeSection === 'properties' ? 'active' : ''}>
            <a data-section="properties" onClick={this.handleSelect}>
              <h4>
                Properties
              </h4>
            </a>
          </li>
          <li className={this.state.activeSection === 'rollouts' ? 'active' : ''}>
            <a data-section="rollouts" onClick={this.handleSelect}>
              <h4>
                Rollouts {runningCount > 0 && (<span className="badge badge-running-count">{runningCount}</span>)}
              </h4>
            </a>
          </li>
        </ul>
        <div className="flex-fill properties-container">
          {this.state.activeSection === 'rollouts' && (
            <FastPropertyRollouts application={application} filters={filters} filtersUpdatedStream={this.filtersUpdatedStream}/>
          )}
          {this.state.activeSection === 'properties' && (
            <FastPropertiesList groupedBy="none" application={application} allProperties={allProperties} filteredProperties={filteredProperties}/>
          )}
        </div>
      </div>
    );
  }

}
