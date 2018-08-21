import * as React from 'react';

import { Application } from 'core/application';
import { ApplicationRefresher } from './ApplicationRefresher';
import { INavigationCategory, navigationCategoryRegistry } from './navigationCategory.registry';
import { PagerDutyButton } from './PagerDutyButton';
import { TinyHeader } from './TinyHeader';
import { ApplicationNavSection } from './ApplicationNavSection';
import { ThirdLevelNavigation } from './ThirdLevelNavigation';
import { ApplicationDataSource } from '../service/applicationDataSource';
import { ReactInjector } from 'core/reactShims';
import { ApplicationIcon } from '../ApplicationIcon';

import './applicationNav.component.less';

export interface IDataSourceCategory extends INavigationCategory {
  dataSources: ApplicationDataSource[];
}

export interface IApplicationHeaderProps {
  app: Application;
}

export interface IApplicationHeaderState {
  primaryCategories?: IDataSourceCategory[];
  secondaryCategories?: IDataSourceCategory[];
  activeCategory?: IDataSourceCategory;
}

export class ApplicationHeader extends React.Component<IApplicationHeaderProps, IApplicationHeaderState> {
  private stopListeningToStateChange: Function;
  private stopListeningToAppRefresh: Function;
  private dataSourceAttribute: any;

  constructor(props: IApplicationHeaderProps) {
    super(props);
    this.configureApplicationEventListeners(props.app);
    this.stopListeningToStateChange = ReactInjector.$uiRouter.transitionService.onSuccess({}, () =>
      this.resetActiveCategory(),
    );
    const categories = this.parseCategories(props);
    this.state = {
      ...categories,
      activeCategory: this.getActiveCategory(categories.primaryCategories.concat(categories.secondaryCategories)),
    };
  }

  public componentWillReceiveProps(nextProps: IApplicationHeaderProps) {
    this.setState(this.parseCategories(nextProps));
    this.configureApplicationEventListeners(nextProps.app);
  }

  private configureApplicationEventListeners(app: Application): void {
    this.clearApplicationListeners();
    this.stopListeningToAppRefresh = app.onRefresh(null, () => {
      // if the user changes the data sources from the config page, regenerate categories
      if (app.attributes.dataSources !== this.dataSourceAttribute) {
        this.dataSourceAttribute = app.attributes.dataSources;
        this.setState(this.parseCategories(this.props));
      }
    });
  }

  private clearApplicationListeners(): void {
    if (this.stopListeningToAppRefresh) {
      this.stopListeningToAppRefresh();
    }
  }

  private parseCategories(props: IApplicationHeaderProps): IApplicationHeaderState {
    const { dataSources } = props.app;
    return {
      primaryCategories: this.getNavigationCategories(dataSources, true),
      secondaryCategories: this.getNavigationCategories(dataSources, false),
    };
  }

  private resetActiveCategory() {
    const activeCategory = this.getActiveCategory(this.state.primaryCategories.concat(this.state.secondaryCategories));
    if (activeCategory !== this.state.activeCategory) {
      this.setState({ activeCategory });
    }
  }

  private getActiveCategory(categories: IDataSourceCategory[]) {
    const active = this.props.app.dataSources.find(
      ds => ds.activeState && ReactInjector.$state.includes(ds.activeState),
    );
    return categories.find(c => c.dataSources.includes(active));
  }

  private getNavigationCategories(dataSources: ApplicationDataSource[], primary: boolean): IDataSourceCategory[] {
    const appSources = dataSources.filter(ds => ds.visible !== false && !ds.disabled && ds.sref);
    const categories: IDataSourceCategory[] = [];
    const allCategories = navigationCategoryRegistry.getAll();
    allCategories.forEach(c => {
      if (c.primary === primary) {
        const matchedSources = appSources.filter(ds => ds.category === c.key);
        if (matchedSources.length) {
          categories.push({ ...c, dataSources: matchedSources });
        }
      }
    });

    const uncategorized = appSources.filter(
      ds => allCategories.every(c => c.key !== ds.category) && ds.primary === primary,
    );
    uncategorized.forEach(ds => {
      categories.push({
        key: ds.key,
        label: ds.label,
        primary: ds.primary,
        order: navigationCategoryRegistry.getHighestOrder() + 100,
        dataSources: [ds],
      });
    });

    categories.sort((a, b) => navigationCategoryRegistry.getOrder(a) - navigationCategoryRegistry.getOrder(b));
    return categories;
  }

  public componentWillUnmount(): void {
    this.stopListeningToStateChange();
    this.clearApplicationListeners();
  }

  public render() {
    const { app } = this.props;
    const { activeCategory, primaryCategories, secondaryCategories } = this.state;
    const ApplicationTitleAndRefresher = (
      <h2 className="horizontal middle">
        <span className="hidden-xs">
          <ApplicationIcon app={app} />
        </span>
        <span className="horizontal middle wrap">
          <span className="application-name">{app.name}</span>
          <ApplicationRefresher app={app} />
        </span>
      </h2>
    );

    return (
      <div>
        <div className="second-level-navigation-header">
          <div className="container application-header horizontal middle">
            {ApplicationTitleAndRefresher}
            <TinyHeader {...this.state} />
            <div className="horizontal space-between flex-1">
              <ApplicationNavSection
                application={app}
                categories={primaryCategories}
                primary={true}
                activeCategory={activeCategory}
              />
              <div className="horizontal middle right">
                <ApplicationNavSection
                  application={app}
                  categories={secondaryCategories}
                  primary={false}
                  activeCategory={activeCategory}
                />
                <PagerDutyButton app={app} />
              </div>
            </div>
          </div>
        </div>
        {app && !app.notFound && <ThirdLevelNavigation category={activeCategory} application={app} />}
      </div>
    );
  }
}
