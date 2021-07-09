import { UISref } from '@uirouter/react';
import SearchApi from 'js-worker-search';
import { groupBy } from 'lodash';
import { Debounce } from 'lodash-decorators';
import { DateTime } from 'luxon';
import React, { ReactNode } from 'react';
import {
  AutoSizer,
  CellMeasurer,
  CellMeasurerCache,
  Column,
  RowMouseEventHandlerParams,
  SortDirection,
  Table,
  TableCellProps,
  TableHeaderProps,
} from 'react-virtualized';
import { forkJoin as observableForkJoin, from as observableFrom } from 'rxjs';

import { PageButton } from './PageButton';
import { ApplicationReader, IApplicationSummary } from '../application';
import { SETTINGS } from '../config';
import { Overridable } from '../overrideRegistry';
import { IOnCall, IPagerDutyService, PagerDutyReader } from './pagerDuty.read.service';
import { Markdown } from '../presentation';
import { ReactInjector } from '../reactShims';
import { relativeTime } from '../utils/timeFormatters';

import './pager.less';

type SortDirectionType = 'ASC' | 'DESC';

export interface IUserDisplay {
  level: number;
  name: string;
  url: string;
}

export interface IUserList {
  [level: number]: IUserDisplay[];
}
export interface IOnCallsByService {
  users?: IUserList;
  applications: IApplicationSummary[];
  last: DateTime;
  service: IPagerDutyService;
  searchString: string;
}

export interface IPagerProps {}

export interface IPagerState {
  accountName: string;
  app: string;
  hideNoApps: boolean;
  notFoundApps: string[];
  filterString: string;
  initialKeys: string[];
  selectedKeys: Map<string, IPagerDutyService>;
  sortBy: string;
  sortDirection: SortDirectionType;
  sortedData: IOnCallsByService[];
}

const paddingStyle = { paddingTop: '15px', paddingBottom: '15px' };

const ServicePill = (props: {
  service: IPagerDutyService;
  changeCallback: (service: IPagerDutyService, value: boolean) => void;
}) => {
  const onClick = () => props.changeCallback(props.service, false);
  return (
    <div className="pill info">
      {props.service.name}
      <i className="fa fa-times-circle clickable" onClick={onClick} />
    </div>
  );
};

const SortIndicator = (props: { direction: SortDirectionType; sorted: boolean }) => {
  if (props.sorted) {
    return props.direction === 'ASC' ? <span className="fa fa-caret-down" /> : <span className="fa fa-caret-up" />;
  }
  return <span className="fa fa-caret-down disabled" />;
};

@Overridable('pager.banner')
class PagerBanner extends React.Component {
  render(): ReactNode {
    return null;
  }
}

export class Pager extends React.Component<IPagerProps, IPagerState> {
  private cache = new CellMeasurerCache({
    defaultHeight: 50,
    fixedWidth: true,
  });
  private allData: IOnCallsByService[] = [];
  private searchApi = new SearchApi();

  constructor(props: IPagerProps) {
    super(props);

    const { $stateParams } = ReactInjector;
    this.state = {
      accountName: (SETTINGS.pagerDuty && SETTINGS.pagerDuty.accountName) || '',
      app: $stateParams.app || '',
      filterString: $stateParams.q || '',
      hideNoApps: $stateParams.hideNoApps || false,
      notFoundApps: [],
      initialKeys: $stateParams.keys || [],
      sortBy: $stateParams.by || 'service',
      sortDirection: $stateParams.direction || SortDirection.ASC,
      sortedData: [],
      selectedKeys: new Map(),
    };
  }

  public componentDidMount(): void {
    // Get the data from all the necessary sources before rendering
    observableForkJoin(
      observableFrom(ApplicationReader.listApplications()),
      PagerDutyReader.listOnCalls(),
      PagerDutyReader.listServices(),
    ).subscribe((results: [IApplicationSummary[], { [id: string]: IOnCall[] }, IPagerDutyService[]]) => {
      const [applications, onCalls, services] = results;
      const sortedData = this.getOnCallsByService(applications, onCalls, services);
      Object.assign(this.allData, sortedData);
      const { app, initialKeys, filterString, hideNoApps, sortBy, sortDirection } = this.state;
      this.runFilter(app, initialKeys, filterString, sortBy, sortDirection, hideNoApps);
    });
  }

  private sortByFunction(a: IOnCallsByService, b: IOnCallsByService, sortBy: string): number {
    if (sortBy === 'service') {
      return a.service.name.localeCompare(b.service.name);
    }
    if (sortBy === 'last') {
      if (!a.last.isValid) {
        return 1;
      }
      if (!b.last.isValid) {
        return -1;
      }
      return a.last.toMillis() < b.last.toMillis() ? 1 : a.last.toMillis() > b.last.toMillis() ? -1 : 0;
    }
    return 0;
  }

  public sort = (info: { sortBy: string; sortDirection: SortDirectionType }): void => {
    const { sortBy, sortDirection } = info;
    const { sortedData } = this.state;

    if (sortBy !== this.state.sortBy || sortDirection !== this.state.sortDirection) {
      ReactInjector.$state.go('.', { by: sortBy, direction: sortDirection });
      this.sortList(sortedData, sortBy, sortDirection);
      this.cache.clearAll();
      this.setState({ sortedData, sortBy, sortDirection });
    }
  };

  public sortList(sortedData: IOnCallsByService[], sortBy: string, sortDirection: SortDirectionType): void {
    if (sortBy) {
      sortedData.sort((a, b) => this.sortByFunction(a, b, sortBy));
      if (sortDirection === SortDirection.DESC) {
        sortedData.reverse();
      }
    }
  }

  private findServiceByApplicationName(applicationName: string): IOnCallsByService {
    return this.allData.find((data) => data.applications.some((application) => application.name === applicationName));
  }

  @Debounce(25)
  private runFilter(
    app: string,
    keys: string[],
    filterString: string,
    sortBy: string,
    sortDirection: SortDirectionType,
    hideNoApps: boolean,
  ) {
    const selectedKeys: Map<string, IPagerDutyService> = new Map();
    if (app) {
      const foundServices: IOnCallsByService[] = [];
      const notFoundApps: string[] = [];

      app.split(',').forEach((applicationName) => {
        const service = this.findServiceByApplicationName(applicationName);
        if (service) {
          foundServices.push(service);
        } else {
          notFoundApps.push(applicationName);
        }
      });

      if (foundServices.length > 0) {
        foundServices.forEach((foundService) =>
          selectedKeys.set(foundService.service.integration_key, foundService.service),
        );
        this.setState({ sortedData: foundServices, selectedKeys, notFoundApps });
        return;
      }

      if (!filterString) {
        filterString = app;
      }
      app = '';
    }

    if (keys && keys.length > 0) {
      const selectedServices = this.allData.filter((data) => keys.includes(data.service.integration_key));
      selectedServices.forEach((s) => selectedKeys.set(s.service.integration_key, s.service));
      this.setState({ selectedKeys });
    }

    ReactInjector.$state.go('.', {
      app,
      q: filterString,
      by: sortBy,
      direction: sortDirection,
      hideNoApps: hideNoApps,
      selectedKeys,
    });

    this.searchApi.search(filterString).then((results: string[]) => {
      let data = results.map((serviceId) => this.allData.find((service) => service.service.id === serviceId));
      if (hideNoApps) {
        data = data.filter((s) => s.applications.length);
      }
      this.sortList(data, sortBy, sortDirection);
      this.cache.clearAll();

      this.setState({ sortedData: data });
    });
  }

  public componentDidUpdate(_prevProps: IPagerProps, prevState: IPagerState): void {
    if (prevState.filterString !== this.state.filterString || prevState.hideNoApps !== this.state.hideNoApps) {
      this.runFilter(
        this.state.app,
        this.state.initialKeys,
        this.state.filterString,
        this.state.sortBy,
        this.state.sortDirection,
        this.state.hideNoApps,
      );
    } else {
      this.sort({ sortBy: this.state.sortBy, sortDirection: this.state.sortDirection });
    }
  }

  private getOnCallsByService(
    applications: IApplicationSummary[],
    onCalls: { [id: string]: IOnCall[] },
    services: IPagerDutyService[],
  ): IOnCallsByService[] {
    const appsByApiKey = groupBy(applications, 'pdApiKey');
    return services
      .filter((a) => a.integration_key) // filter out services without an integration_key
      .map((service) => {
        // connect the users attached to the service by way of escalation policy
        let users: IUserList;
        const searchTokens: string[] = [service.name];
        const levels = onCalls[service.policy];
        if (levels) {
          users = groupBy(
            levels
              .map((level) => {
                return level.user
                  ? { name: level.user.summary, url: level.user.html_url, level: level.escalation_level }
                  : undefined;
              })
              .filter((a) => a),
            'level',
          );
          searchTokens.push(...levels.map((level) => (level.user ? level.user.summary : undefined)).filter((n) => n));
        }

        // Get applications associated with the service key
        const apiKey = service.integration_key;
        const associatedApplications = appsByApiKey[apiKey] ?? [];
        searchTokens.push(...associatedApplications.map((app) => `${app.name},${app.aliases || ''}`));

        const onCallsByService = {
          users,
          applications: associatedApplications,
          service,
          last: DateTime.fromISO((service as any).lastIncidentTimestamp),
          searchString: searchTokens.join(' '),
        };
        this.searchApi.indexDocument(onCallsByService.service.id, onCallsByService.searchString);
        return onCallsByService;
      });
  }

  private selectedChanged = (service: IPagerDutyService, value: boolean): void => {
    const { selectedKeys } = this.state;
    value ? selectedKeys.set(service.integration_key, service) : selectedKeys.delete(service.integration_key);
    ReactInjector.$state.go('.', { keys: Array.from(selectedKeys.keys()) });
    this.setState({ selectedKeys });
  };

  private clearAll = (): void => {
    const { selectedKeys } = this.state;
    selectedKeys.clear();
    this.setState({ selectedKeys });
  };

  private rowGetter = (data: { index: number }): any => {
    return this.state.sortedData[data.index];
  };

  private serviceRenderer = (data: TableCellProps): React.ReactNode => {
    const service: IPagerDutyService = data.cellData;
    return (
      <div style={paddingStyle}>
        <a
          title={service.name}
          href={`https://${this.state.accountName}.pagerduty.com/services/${service.id}`}
          target="_blank"
        >
          <Markdown tag="span" message={this.highlight(service.name)} />
        </a>
      </div>
    );
  };

  private lastIncidentRenderer = (data: TableCellProps): React.ReactNode => {
    const time: DateTime = data.cellData;
    return <div style={paddingStyle}>{time.isValid ? relativeTime(time.toMillis()) : 'Never'}</div>;
  };

  private applicationRenderer = (data: TableCellProps): React.ReactNode => {
    const apps: IApplicationSummary[] = data.cellData;
    const appList = apps.map((app) => {
      let displayName = app.name;
      if (app.aliases) {
        displayName = `${displayName} (${app.aliases.replace(/,/g, ', ')})`;
      }

      return (
        <li key={app.name}>
          <UISref to="home.applications.application" params={{ application: app.name }}>
            <a>
              <Markdown message={this.highlight(displayName)} tag="span" />
            </a>
          </UISref>
        </li>
      );
    });

    return (
      <CellMeasurer
        cache={this.cache}
        columnIndex={data.columnIndex}
        key={data.dataKey}
        parent={data.parent}
        rowIndex={data.rowIndex}
      >
        <div style={paddingStyle}>
          <ul className="page-app-list">{appList}</ul>
        </div>
      </CellMeasurer>
    );
  };

  private highlight(text: string): string {
    const match = this.state.filterString || this.state.app;
    if (match) {
      const re = new RegExp(match, 'gi');
      return text.replace(re, '<span class="highlighted">$&</span>');
    }
    return text;
  }

  private onCallRenderer = (data: TableCellProps): React.ReactNode => {
    const onCalls: IUserList = data.cellData;
    return (
      <CellMeasurer
        cache={this.cache}
        columnIndex={data.columnIndex}
        key={data.dataKey}
        parent={data.parent}
        rowIndex={data.rowIndex}
      >
        <div style={paddingStyle}>
          {onCalls
            ? Object.keys(onCalls).map((level) => {
                return (
                  <div key={level} className="users">
                    <div className="user-level">{level}</div>
                    <div className="user-names">
                      {onCalls[Number(level)]
                        .filter((user) => !user.name.includes('ExcludeFromAudit'))
                        .map((user, index) => (
                          <a key={index} target="_blank" href={user.url}>
                            <Markdown tag="span" message={this.highlight(user.name)} />
                          </a>
                        ))}
                    </div>
                  </div>
                );
              })
            : 'Nobody'}
        </div>
      </CellMeasurer>
    );
  };

  private pageRenderer = (data: TableCellProps): React.ReactNode => {
    const service: IPagerDutyService = data.cellData;

    const checked = this.state.selectedKeys.has(service.integration_key);
    return (
      <div style={paddingStyle}>
        <div className={`page-checkbox ${checked ? 'checked' : ''}`}>
          <i className="fa fa-check" />
        </div>
      </div>
    );
  };

  private pageHeaderRenderer = (_data: TableHeaderProps): React.ReactNode => {
    return <span />;
  };

  private headerRenderer = (data: TableHeaderProps): React.ReactNode => {
    const { dataKey, disableSort, label, sortBy, sortDirection } = data;
    const children = [
      <span className="table-header" key="label" title={label}>
        {label}
      </span>,
    ];

    if (!disableSort) {
      children.push(<SortIndicator key="SortIndicator" direction={sortDirection} sorted={sortBy === dataKey} />);
    }

    return children;
  };

  private handleFilterChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.setState({ app: '', filterString: event.target.value });
  };

  private rowClassName = (info: { index: number }): string => {
    if (info.index === -1) {
      return 'on-call-header';
    }

    const classNames = ['on-call-row'];
    const onCallsByService = this.state.sortedData[info.index];
    if (this.state.selectedKeys.get(onCallsByService.service.integration_key)) {
      classNames.push('selected');
    }
    if (onCallsByService.service.status === 'disabled') {
      classNames.push('disabled');
    }
    return classNames.join(' ');
  };

  private handleHideNoAppsChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const hideNoApps = event.target.checked;
    this.setState({ hideNoApps });
    ReactInjector.$state.go('.', { hideNoApps });
  };

  private rowClicked = (info: RowMouseEventHandlerParams): void => {
    // Don't change selection if clicking a link...
    if ((info.event.target as HTMLElement).closest('A') !== null) {
      return;
    }
    const service: IPagerDutyService = (info.rowData as any).service;
    if (service.status !== 'disabled') {
      const flippedValue = !this.state.selectedKeys.get(service.integration_key);
      this.selectedChanged(service, flippedValue);
    }
  };

  private closeCallback = (succeeded: boolean): void => {
    if (succeeded) {
      this.setState({ selectedKeys: new Map() });
    }
  };

  public render() {
    const { app, filterString, hideNoApps, notFoundApps, selectedKeys, sortBy, sortDirection, sortedData } = this.state;

    const forceOpen =
      app && selectedKeys.size === sortedData.length && sortedData.length !== 0 && notFoundApps.length === 0;

    return (
      <div className="infrastructure">
        <div className="infrastructure-section pager-header">
          <div className="container">
            <h2 className="header-section">
              <div className="flex-grow">
                <div className="pager">
                  <i className="fa fa-bullhorn" />
                  <span className="pager-label">Pager</span>
                </div>
              </div>
            </h2>
          </div>
          <PagerBanner />
        </div>

        <div className="container main-content on-call scrollable-columns">
          {notFoundApps.length > 0 && (
            <div className="alert alert-warning">
              <p>
                PagerDuty Services were not found for the following applications:{' '}
                {notFoundApps.map((applicationName, i) => (
                  <>
                    <UISref to="home.applications.application" params={{ application: applicationName }}>
                      <a className="clickable">{applicationName}</a>
                    </UISref>
                    {i + 1 < notFoundApps.length && ', '}
                  </>
                ))}
              </p>
            </div>
          )}
          <div className="on-call-filter">
            <span>Filter </span>
            <input
              type="text"
              value={filterString}
              onChange={this.handleFilterChange}
              placeholder="Service, application, name"
            />
            <label className="hide-checkbox">
              <input type="checkbox" checked={hideNoApps} onChange={this.handleHideNoAppsChanged} />
              Hide services with no associated apps
            </label>
          </div>
          <div className="table on-call-table">
            <AutoSizer>
              {({ height, width }) => (
                <Table
                  headerClassName="table-header"
                  headerHeight={26}
                  onRowClick={this.rowClicked}
                  overscanRowCount={20}
                  rowGetter={this.rowGetter}
                  rowHeight={this.cache.rowHeight}
                  rowCount={sortedData.length}
                  rowClassName={this.rowClassName}
                  sort={this.sort}
                  sortBy={sortBy}
                  sortDirection={sortDirection}
                  width={width}
                  height={height}
                >
                  <Column
                    disableSort={true}
                    dataKey="service"
                    width={25}
                    className="col-page"
                    headerRenderer={this.pageHeaderRenderer}
                    cellRenderer={this.pageRenderer}
                  />
                  <Column
                    dataKey="service"
                    label="PagerDuty Service Name"
                    cellRenderer={this.serviceRenderer}
                    className="col-service"
                    headerRenderer={this.headerRenderer}
                    width={180}
                  />
                  <Column
                    disableSort={true}
                    width={210}
                    label="Application Name(s)"
                    cellRenderer={this.applicationRenderer}
                    headerRenderer={this.headerRenderer}
                    className="col-application"
                    dataKey="applications"
                    flexGrow={1}
                  />
                  <Column
                    disableSort={true}
                    width={180}
                    dataKey="users"
                    label="Currently On-call"
                    cellRenderer={this.onCallRenderer}
                    className="col-on-call"
                    headerRenderer={this.headerRenderer}
                  />
                  <Column
                    width={120}
                    dataKey="last"
                    label="Last Incident"
                    className="col-incident"
                    cellRenderer={this.lastIncidentRenderer}
                    headerRenderer={this.headerRenderer}
                  />
                </Table>
              )}
            </AutoSizer>
          </div>
        </div>
        <div className="main-footer on-call-footer">
          <div className="selected-policies">
            <span className="selected-count">
              {selectedKeys.size} {selectedKeys.size === 1 ? 'policy' : 'policies'} selected{' '}
            </span>
            <div className="selected-pills">
              {Array.from(selectedKeys.values()).map((service) => (
                <ServicePill key={service.integration_key} service={service} changeCallback={this.selectedChanged} />
              ))}
            </div>
          </div>
          <div>
            <button
              disabled={selectedKeys.size === 0}
              className="btn btn-sm btn-default"
              style={{ marginRight: '5px' }}
              onClick={this.clearAll}
            >
              Clear All
            </button>
            <PageButton
              closeCallback={this.closeCallback}
              disabled={selectedKeys.size === 0}
              forceOpen={forceOpen}
              services={Array.from(selectedKeys.values())}
            />
          </div>
        </div>
      </div>
    );
  }
}
