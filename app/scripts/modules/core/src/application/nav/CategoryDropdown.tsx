import * as React from 'react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import { UISref, UISrefActive } from '@uirouter/react';
import { Subscription } from 'rxjs';
import { BindAll } from 'lodash-decorators';
import { Dropdown } from 'react-bootstrap';
import { merge } from 'rxjs/observable/merge';

import { Application, ApplicationDataSource } from 'core/application';
import { IEntityTags } from 'core/domain';
import { DataSourceNotifications } from 'core/entityTag/notifications/DataSourceNotifications';
import { ReactInjector } from 'core/reactShims';

import { NavIcon } from './NavIcon';
import { IDataSourceCategory } from './ApplicationHeader';

export interface ICategoryDropdownProps {
  category: IDataSourceCategory;
  activeCategory: IDataSourceCategory;
  application: Application;
}

export interface ICategoryDropdownState {
  open: boolean;
  runningCount?: number;
  tags?: IEntityTags[];
}

@UIRouterContext
@BindAll()
export class CategoryDropdown extends React.Component<ICategoryDropdownProps, ICategoryDropdownState> {

  private runningCountSubscription: Subscription;
  private entityTagsSubscription: Subscription;

  constructor(props: ICategoryDropdownProps) {
    super(props);
    this.state = { open: false };
  }

  public componentDidMount() {
    this.configureSubscriptions(this.props);
  }

  public componentWillReceiveProps(nextProps: ICategoryDropdownProps) {
    this.configureSubscriptions(nextProps);
  }

  private configureSubscriptions(props: ICategoryDropdownProps) {
    const { category, application } = props;
    const withBadges = category.dataSources.filter(ds => ds.badge).map(ds => application.getDataSource(ds.badge));
    this.runningCountSubscription = merge(...withBadges.map(ds => ds.refresh$))
      .subscribe(() => {
        this.setState({ runningCount: withBadges.reduce((acc: number, ds: ApplicationDataSource) => acc + ds.data.length, 0) })
      });
    this.entityTagsSubscription = merge(...category.dataSources.map(ds => ds.refresh$))
      .subscribe(() => {
        const tags = category.dataSources.reduce((acc: IEntityTags[], ds: ApplicationDataSource) => acc.concat(ds.alerts), []);
        this.setState({ tags });
      })
  }

  private clearSubscriptions(): void {
    this.runningCountSubscription.unsubscribe();
    this.entityTagsSubscription.unsubscribe();
  }

  public componentWillUnmount() {
    this.clearSubscriptions();
  }

  private open(): void {
    // don't open if it's already expanded as the third-level navigation
    if (this.props.activeCategory === this.props.category) {
      return;
    }
    this.setState({ open: true });
  }

  private close(): void {
    this.setState({ open: false });
  }

  private createNonMenuEntry(): JSX.Element {
    const { runningCount } = this.state;
    const { category, application } = this.props;
    const dataSource = category.dataSources[0];
    return (
      <UISrefActive class="active" key={category.key}>
        <UISref to={dataSource.sref}>
          <a className="nav-item top-level">
            <NavIcon icon={dataSource.icon}/>
            {' ' + dataSource.label}
            <DataSourceNotifications tags={dataSource.alerts} application={application} tabName={category.label}/>
            {runningCount > 0 && <span className="badge badge-running-count">{runningCount}</span>}
          </a>
        </UISref>
      </UISrefActive>
    )
  }

  private createMenuTitle(isActive: boolean): JSX.Element {
    const { runningCount, tags } = this.state;
    const { category, application } = this.props;
    return (
      <span className={`horizontal middle ${isActive ? 'active' : ''}`}>
        <NavIcon icon={category.icon}/>
        {' ' + category.label}
        {runningCount > 0 && <span className="badge badge-running-count">{runningCount}</span>}
        <DataSourceNotifications tags={tags} application={application} tabName={category.label}/>
      </span>
    );
  }

  private createMenuItem(dataSource: ApplicationDataSource): JSX.Element {
    const { category, application } = this.props;
    return (
      <li onClick={this.close} key={dataSource.key}>
        <UISrefActive class="active" key={category.key}>
          <UISref to={dataSource.sref}>
            <a className="nav-menu-item horizontal middle">
              {' ' + dataSource.label}
              <DataSourceNotifications tags={dataSource.alerts} application={application} tabName={category.label}/>
              {dataSource.badge && application.getDataSource(dataSource.badge).data.length > 0 &&
              <span className="badge badge-running-count">{application.getDataSource(dataSource.badge).data.length}</span>}
            </a>
          </UISref>
        </UISrefActive>
      </li>
    );
  }

  public render() {
    const { open } = this.state;
    const { category } = this.props;
    if (category.dataSources.length === 1) {
      return this.createNonMenuEntry();
    }
    const isActive = category.dataSources.some(ds => ReactInjector.$state.includes(ds.activeState));
    return (
      <Dropdown
        onMouseEnter={this.open}
        onMouseLeave={this.close}
        key={category.key}
        id={`menu-${category.key}`}
        className={open ? 'open' : ''}
      >
        <Dropdown.Toggle
          bsStyle="link"
          className={`nav-item horizontal middle ${isActive ? 'active' : ''}`}
          noCaret={true}
        >
          {this.createMenuTitle(isActive)}
        </Dropdown.Toggle>
        <Dropdown.Menu
          open={open}
          className={open ? 'open' : ''}
        >
          {category.dataSources.map(dataSource => this.createMenuItem(dataSource))}
        </Dropdown.Menu>
      </Dropdown>
    );
  }
}
