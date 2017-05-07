import * as React from 'react';
import { groupBy } from 'lodash';
import { Subscription } from 'rxjs/Subscription';
import autoBindMethods from 'class-autobind-decorator';
import { Subject } from 'rxjs/Subject';
import { Tooltip } from 'react-bootstrap';

import { Property } from '../domain/property.domain';
import { Application } from 'core/application/application.model';
import { NetflixSettings } from 'netflix/netflix.settings';
import { ApplicationDataSource } from 'core/application/service/applicationDataSource';
import { FastPropertyRollouts } from './rollouts/FastPropertyRollouts';
import { FastPropertiesList } from './FastPropertiesList';
import { StickyContainer } from 'core/utils/stickyHeader/StickyContainer';
import { FastPropertyFilterSearch } from './filter/FastPropertyFilterSearch';
import { IFilterTag, FilterTags } from 'core/filterModel/FilterTags';
import { modalService } from 'core/modal.service';
import { $stateParams } from 'core/uirouter';
import { stateEvents } from 'core/state.events';
import { sortProperties } from '../global/GlobalPropertiesList';

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
  sortBy: string;
}

@autoBindMethods
export class ApplicationProperties extends React.Component<IProps, IState> {

  private filtersUpdatedStream: Subject<IFilterTag[]> = new Subject<IFilterTag[]>();
  private dataSourceUnsubscribe: () => any;
  private dataSource: ApplicationDataSource;
  private stateChangeListener: Subscription;

  constructor(props: IProps) {
    super(props);
    this.state = {
      enabled: NetflixSettings.feature.fastProperty,
      loading: true,
      filters: [],
      filteredProperties: [],
      sortBy: $stateParams.sortBy || 'key'
    };
    this.dataSource = this.props.application.getDataSource('properties');
    this.dataSource.activate();
    this.dataSource.ready().then(() => this.dataUpdated());
    this.dataSourceUnsubscribe = this.dataSource.onRefresh(null,
      () => this.dataUpdated(),
      () => this.dataLoadError(),
    );
    this.filtersUpdatedStream.subscribe((newTags) => this.applyFilters(newTags));
    this.stateChangeListener = stateEvents.stateChangeSuccess.subscribe(() => this.applyFilters(this.state.filters));
  }

  public componentWillUnmount() {
    this.filtersUpdatedStream = null;
    this.stateChangeListener.unsubscribe();
    this.dataSourceUnsubscribe();
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
    modalService.open({
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

  public render() {
    const { application } = this.props;
    const { filters, allProperties, filteredProperties } = this.state;

    return (
      <div className="flex-fill">
        {this.state.loading && (<h3 className="text-center"><span className="fa fa-cog fa-spin"/></h3>)}
        {!this.state.loading && (
          <div className="fast-properties flex-fill">
            <div className="form form-inline header">
              <div className="form-group">
                <h3>Properties</h3>
                <FastPropertyFilterSearch properties={this.state.allProperties} filtersUpdatedStream={this.filtersUpdatedStream}/>
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
            <StickyContainer className="flex-fill properties-container">
              <FastPropertyRollouts application={application} filters={filters} filtersUpdatedStream={this.filtersUpdatedStream}/>
              <FastPropertiesList groupedBy="none" application={application} allProperties={allProperties} filteredProperties={filteredProperties}/>
            </StickyContainer>
          </div>
        )}
      </div>
    );
  }

}
