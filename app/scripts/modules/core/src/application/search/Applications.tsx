import { isEqual } from 'lodash';
import React from 'react';
import { SelectCallback } from 'react-bootstrap';
import { BehaviorSubject, from as observableFrom, Subject } from 'rxjs';
import { combineLatest as observableCombineLatest } from 'rxjs';
import { distinctUntilChanged, map, takeUntil } from 'rxjs/operators';

import { ApplicationTable } from './ApplicationsTable';
import { PaginationControls } from './PaginationControls';
import { IAccount } from '../../account';
import { ICache, ViewStateCache } from '../../cache';
import { InsightMenu } from '../../insight/InsightMenu';
import { ApplicationReader, IApplicationSummary } from '../service/ApplicationReader';
import { Spinner } from '../../widgets';

import '../applications.less';

interface IViewState {
  filter: string;
  sort: string;
}

export interface IApplicationPagination {
  currentPage: number;
  itemsPerPage: number;
  maxSize: number;
}

export interface IApplicationsState {
  accounts: IAccount[];
  applications: IApplicationSummary[];
  errorState: boolean;
  pagination: IApplicationPagination;
}

export class Applications extends React.Component<{}, IApplicationsState> {
  private applicationsCache: ICache;

  private filter$ = new BehaviorSubject<string>(null);
  private sort$ = new BehaviorSubject<string>(null);
  private pagination$ = new BehaviorSubject<IApplicationPagination>(this.getDefaultPagination());
  private destroy$ = new Subject();

  constructor(props: {}) {
    super(props);
    this.applyCachedViewState();
    const pagination = this.getDefaultPagination();
    this.state = { pagination } as IApplicationsState;
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private applyCachedViewState() {
    this.applicationsCache =
      ViewStateCache.get('applications') || ViewStateCache.createCache('applications', { version: 2 });
    const viewState: IViewState = this.applicationsCache.get('#global') || { filter: '', sort: '+name' };
    this.filter$.next(viewState.filter);
    this.sort$.next(viewState.sort);
  }

  public componentDidMount() {
    const appMatchesQuery = (query: string, app: IApplicationSummary) => {
      const searchableValues = [app.name, app.email, app.accounts, app.description]
        .filter((f) => !!f)
        .map((f) => f.toLowerCase());
      return searchableValues.some((value) => value.includes(query));
    };

    const appSort = (column: string, a: any, b: any) => {
      const reverse = column[0] === '-';
      const key = reverse ? column.slice(1) : column;
      return (a[key] || '').localeCompare(b[key] || '') * (reverse ? -1 : 1);
    };

    const applicationSummaries$ = observableFrom(ApplicationReader.listApplications()).pipe(
      map((apps) => apps.map((app) => this.fixAccount(app))),
    );

    const filteredApps$ = observableCombineLatest([applicationSummaries$, this.filter$, this.sort$]).pipe(
      // Apply filter/sort
      map(([apps, filter, sort]) => {
        const viewState: IViewState = { filter, sort };
        this.applicationsCache.put('#global', viewState);
        return apps.filter((app) => appMatchesQuery(filter, app)).sort((a, b) => appSort(sort, a, b));
      }),
    );

    // validate and update pagination
    observableCombineLatest([filteredApps$, this.pagination$.pipe(distinctUntilChanged(isEqual))])
      .pipe(
        map(([applications, pagination]) => {
          const lastPage = Math.floor(applications.length / pagination.itemsPerPage) + 1;
          const currentPage = Math.min(pagination.currentPage, lastPage);
          const maxSize = applications.length;
          const validatedPagination = { ...pagination, currentPage, maxSize } as IApplicationPagination;
          return { applications, pagination: validatedPagination };
        }),
        takeUntil(this.destroy$),
      )
      .subscribe(
        ({ applications, pagination }) => {
          const { currentPage, itemsPerPage } = pagination;
          const start = (currentPage - 1) * itemsPerPage;
          const end = start + itemsPerPage;
          this.setState({ applications: applications.slice(start, end), pagination });
        },
        (error) => {
          this.setState({ errorState: true });
          throw error;
        },
      );
  }

  private toggleSort(column: string): void {
    const current = this.sort$.getValue();
    const newSort = current === column ? `-${column}` : column;
    this.sort$.next(newSort);
  }

  private fixAccount(application: IApplicationSummary): IApplicationSummary {
    if (application.accounts) {
      application.accounts = application.accounts.split(',').sort().join(', ');
    }
    return application;
  }

  private getDefaultPagination(): IApplicationPagination {
    return {
      currentPage: 1,
      itemsPerPage: 12,
      maxSize: 12,
    };
  }

  public render() {
    const { applications, pagination, errorState } = this.state;
    const { maxSize, currentPage, itemsPerPage } = pagination;
    const currentSort = this.sort$.value;
    const changePage: SelectCallback = (page: any) => {
      return this.pagination$.next({ ...pagination, currentPage: page });
    };

    const LoadingSpinner = () => (
      <div className="horizontal middle center" style={{ marginBottom: '250px', height: '150px' }}>
        <Spinner size="medium" />
      </div>
    );

    const ErrorIndicator = () => (
      <div className="horizontal middle center heading-4" style={{ marginBottom: '250px', height: '150px' }}>
        <i className="fa fa-exclamation-triangle" style={{ paddingRight: '8px' }} />
        <span>
          Error fetching applications. Check that your gate endpoint is accessible. Further information on
          troubleshooting this error is available <a href="https://www.spinnaker.io/setup/quickstart/faq/">here</a>.
        </span>
      </div>
    );

    const ApplicationData = () => {
      if (errorState) {
        return <ErrorIndicator />;
      }

      if (!applications) {
        return <LoadingSpinner />;
      }

      if (applications.length === 0) {
        return <h4>No matches found for '{this.filter$.value}'</h4>;
      }

      return (
        <div className="infrastructure-section">
          <ApplicationTable
            currentSort={currentSort}
            applications={applications}
            toggleSort={(column) => this.toggleSort(column)}
          />
          <PaginationControls
            onPageChanged={changePage}
            activePage={currentPage}
            totalPages={Math.ceil(maxSize / itemsPerPage)}
          />
        </div>
      );
    };

    return (
      <div className="infrastructure">
        <div className="infrastructure-section search-header">
          <div className="container">
            <h2 className="header-section">
              <span className="search-label">Applications</span>
              <input
                type="search"
                placeholder="Search applications"
                className="form-control input-md"
                ref={(input) => input && input.focus()}
                onChange={(evt) => this.filter$.next(evt.target.value)}
                value={this.filter$.value}
              />
            </h2>
            <div className="header-actions">
              <InsightMenu createApp={true} createProject={false} refreshCaches={false} />
            </div>
          </div>
        </div>

        <div className="infrastructure-section container">
          <ApplicationData />
        </div>
      </div>
    );
  }
}
