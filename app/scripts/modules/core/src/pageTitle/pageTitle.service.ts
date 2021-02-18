import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { Rejection, RejectType, StateParams, TransitionService } from '@uirouter/core';
import { IScope, module } from 'angular';

export interface IMainConfig {
  field: string;
  label: string;
}

export interface ISectionConfig {
  title: string;
}

export interface IDetailsConfig {
  title: string;
  nameParam?: string;
  accountParam?: string;
  regionParam?: string;
}

export interface IStatePageData {
  pageTitleMain?: IMainConfig;
  pageTitleSection?: ISectionConfig;
  pageTitleDetails?: IDetailsConfig;
}

export interface IPageDataParts {
  main: string;
  section: string;
  details: string;
}

export class PageTitleService {
  private previousPageTitle = 'Spinnaker';
  private routeCount = 0;

  public static $inject = ['$rootScope', '$stateParams', '$transitions'];
  constructor(private $rootScope: IScope, private $stateParams: StateParams, $transitions: TransitionService) {
    document.title = 'Spinnaker: Loading...';
    $transitions.onStart({}, (transition) => {
      this.handleRoutingStart();
      const onSuccess = () => this.handleRoutingSuccess(transition.to().data);
      const onReject = (err: Rejection) => this.handleRoutingError(err);
      transition.promise.then(onSuccess, onReject);
    });
  }

  public handleRoutingStart(): void {
    this.routeCount++;
    this.previousPageTitle = document.title;
    this.setRoutingFlag();
    document.title = 'Spinnaker: Loading...';
  }

  public handleRoutingError(rejection: Rejection): void {
    this.routeCount--;
    this.setRoutingFlag();
    const cancelled = rejection.type === RejectType.ABORTED;
    document.title = cancelled ? this.previousPageTitle : 'Spinnaker: Error';
  }

  public handleRoutingSuccess(config: IStatePageData): void {
    const parts: IPageDataParts = this.configurePageTitle(config);
    let title = parts.main || 'Spinnaker';
    if (parts.section) {
      title += ' · ' + parts.section;
    }
    if (parts.details) {
      title += ' · ' + parts.details;
    }
    this.$rootScope.routing = false;
    document.title = title;
  }

  public resolveStateParams(config: IDetailsConfig): string {
    if (!config) {
      return null;
    }

    const { $stateParams } = this;
    const { title, nameParam, accountParam, regionParam } = config;

    let result = title;

    if (nameParam) {
      result += ': ' + $stateParams[nameParam];
    }

    if (accountParam || regionParam) {
      result += ' (';
      if (accountParam && regionParam) {
        result += $stateParams[accountParam] + ':' + $stateParams[regionParam];
      } else {
        result += $stateParams[accountParam] || $stateParams[regionParam];
      }
      result += ')';
    }

    return result;
  }

  public configureSection(sectionConfig: ISectionConfig): string {
    return this.resolveStateParams(sectionConfig);
  }

  public configureDetails(detailsConfig: IDetailsConfig): string {
    return this.resolveStateParams(detailsConfig);
  }

  public configureMain(mainConfig: IMainConfig): string {
    const { $stateParams } = this;
    let main = null;
    if (!mainConfig) {
      return main;
    }
    if (mainConfig.field) {
      main = $stateParams[mainConfig.field];
    }
    if (mainConfig.label) {
      main = mainConfig.label;
    }
    return main;
  }

  public configurePageTitle(data: IStatePageData = {}): IPageDataParts {
    return {
      main: this.configureMain(data.pageTitleMain),
      section: this.configureSection(data.pageTitleSection),
      details: this.configureDetails(data.pageTitleDetails),
    };
  }

  private setRoutingFlag() {
    this.$rootScope.routing = this.routeCount > 0;
  }
}

export const PAGE_TITLE_SERVICE = 'spinnaker.core.pageTitle.service';

module(PAGE_TITLE_SERVICE, [UIROUTER_ANGULARJS])
  .service('pageTitleService', PageTitleService)
  .run(['pageTitleService', (pageTitleService: PageTitleService) => pageTitleService]);
