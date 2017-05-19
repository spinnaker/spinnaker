import * as React from 'react';
import { Subject } from 'rxjs';

import { Application, ApplicationDataSource, FilterTags, IExecution, IFilterTag } from '@spinnaker/core';

import { NetflixReactInjector } from 'netflix/react.injector';
import { Property } from '../domain/property.domain';
import { FastPropertyRollouts } from '../view/rollouts/FastPropertyRollouts';

interface IProps {
  app: Application,
}

interface IState {
  filters: IFilterTag[];
  extractedProperties: Property[];
}

export class GlobalRollouts extends React.Component<IProps, IState> {

  private filtersUpdatedStream: Subject<IFilterTag[]> = new Subject<IFilterTag[]>();
  private dataSourceUnsubscribe: () => any;
  private dataSource: ApplicationDataSource;
  private runningDataSource: ApplicationDataSource;

  constructor(props: IProps) {
    super(props);
    this.state = {
      filters: [],
      extractedProperties: [],
    };

    this.filtersUpdatedStream.subscribe((filters) => this.setState({filters}));

    this.dataSource = props.app.getDataSource('propertyPromotions');
    this.dataSource.activate();
    this.runningDataSource = props.app.getDataSource('runningPropertyPromotions');
    this.runningDataSource.activate();
    this.dataSourceUnsubscribe = this.dataSource.onRefresh(null, () => this.extractProperties());
  }

  public componentWillUnmount() {
    this.filtersUpdatedStream = null;
    this.dataSource.deactivate();
    this.runningDataSource.deactivate();
    if (this.dataSourceUnsubscribe) {
      this.dataSourceUnsubscribe();
    }
  }

  public clearFilters(): void {
    this.filtersUpdatedStream.next([]);
  }


  private extractProperties(): void {
    const properties: any[] = [];
    this.dataSource.data.forEach((execution: IExecution) => {
      properties.push(...execution.context.persistedProperties || []);
      properties.push(...execution.context.originalProperties || []);
    });
    this.setState({extractedProperties: properties.map(Property.from)});
  }


  public render() {
    const { filters, extractedProperties } = this.state;
    return (
      <div className="flex-fill">
        <div className="fast-properties global-rollouts flex-fill">
          <div className="form form-inline header">
            <div className="form-group">
              <h3>Property Rollouts</h3>
              <NetflixReactInjector.FastPropertyFilterSearch properties={extractedProperties} filtersUpdatedStream={this.filtersUpdatedStream}/>
            </div>
            <div className="fast-property-filter-tags">
              <FilterTags tags={filters} clearFilters={this.clearFilters}/>
            </div>
          </div>
          <FastPropertyRollouts application={this.props.app} filters={filters} filtersUpdatedStream={this.filtersUpdatedStream}/>
        </div>
      </div>
    );
  }

}
