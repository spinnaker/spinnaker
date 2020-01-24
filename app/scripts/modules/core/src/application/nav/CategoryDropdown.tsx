import { MenuTitle } from './MenuTitle';
import React from 'react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import { UISref, UISrefActive } from '@uirouter/react';
import { Subscription } from 'rxjs';
import { Dropdown } from 'react-bootstrap';
import { merge } from 'rxjs/observable/merge';

import { Application, ApplicationDataSource } from 'core/application';
import { IEntityTags } from 'core/domain';
import { noop } from 'core/utils';
import { DataSourceNotifications } from 'core/entityTag/notifications/DataSourceNotifications';
import { ReactInjector } from 'core/reactShims';

import { NavIcon } from './NavIcon';
import { IDataSourceCategory } from './ApplicationHeader';
import { DataSourceEntry } from './DataSourceEntry';

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
    if (nextProps.activeCategory === this.props.category) {
      this.close();
    }
    this.clearSubscriptions();
    this.configureSubscriptions(nextProps);
  }

  private configureSubscriptions(props: ICategoryDropdownProps) {
    const { category, application } = props;
    const withBadges = category.dataSources.filter(ds => ds.badge).map(ds => application.getDataSource(ds.badge));
    this.runningCountSubscription = merge(...withBadges.map(ds => ds.refresh$)).subscribe(() => {
      this.setState({
        runningCount: withBadges.reduce((acc: number, ds: ApplicationDataSource<any[]>) => acc + ds.data.length, 0),
      });
    });
    if (category.key === 'delivery') {
      const withTags = [application.getDataSource('executions'), application.getDataSource('pipelineConfigs')];
      this.entityTagsSubscription = merge(...withTags.map(ds => ds.refresh$)).subscribe(() => {
        const tags = withTags.reduce(
          (acc: IEntityTags[], ds: ApplicationDataSource) => acc.concat(ds.alerts || []),
          [],
        );
        this.setState({ tags });
      });
    } else {
      this.entityTagsSubscription = merge(...category.dataSources.map(ds => ds.refresh$)).subscribe(() => {
        const tags = category.dataSources.reduce(
          (acc: IEntityTags[], ds: ApplicationDataSource) => acc.concat(ds.alerts || []),
          [],
        );
        this.setState({ tags });
      });
    }
  }

  private clearSubscriptions(): void {
    this.runningCountSubscription.unsubscribe();
    this.entityTagsSubscription.unsubscribe();
  }

  public componentWillUnmount() {
    this.clearSubscriptions();
  }

  private open = (): void => {
    // don't open if it's already expanded as the third-level navigation
    if (this.props.activeCategory === this.props.category) {
      return;
    }
    this.setState({ open: true });
  };

  private close = (): void => {
    this.setState({ open: false });
  };

  private createNonMenuEntry(): JSX.Element {
    const { runningCount, tags } = this.state;
    const { category, application } = this.props;
    const dataSource = category.dataSources[0];
    return (
      <UISrefActive class="active" key={category.key}>
        <UISref to={dataSource.sref}>
          <a className="nav-item top-level horizontal middle">
            <NavIcon icon={dataSource.icon} />
            {' ' + dataSource.label}
            {runningCount > 0 && <span className="badge badge-running-count">{runningCount}</span>}
            <DataSourceNotifications tags={tags} application={application} tabName={category.label} />
          </a>
        </UISref>
      </UISrefActive>
    );
  }

  private createMenuItem(dataSource: ApplicationDataSource): JSX.Element {
    const { category, application } = this.props;
    return (
      <li onClick={this.close} key={dataSource.key}>
        <UISrefActive class="active" key={category.key}>
          <UISref to={dataSource.sref}>
            <a className="nav-menu-item horizontal middle">
              <DataSourceEntry application={application} dataSource={dataSource} hideIcon={true} />
            </a>
          </UISref>
        </UISrefActive>
      </li>
    );
  }

  public render() {
    const { open, runningCount, tags } = this.state;
    const { category, application } = this.props;
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
        open={open}
        onToggle={noop} // the UiSref on .Toggle handles navigation, but the component complains if this prop is missing
        className={open ? 'open' : ''}
      >
        <MenuTitle
          bsRole="toggle"
          isActive={isActive}
          category={category}
          application={application}
          runningCount={runningCount}
          tags={tags}
          closeMenu={this.close}
        />
        <Dropdown.Menu>{category.dataSources.map(dataSource => this.createMenuItem(dataSource))}</Dropdown.Menu>
      </Dropdown>
    );
  }
}
