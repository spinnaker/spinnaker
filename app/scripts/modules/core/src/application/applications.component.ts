import { IController, IComponentOptions, IFilterService, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { StateService } from '@uirouter/angularjs';

import { ACCOUNT_SERVICE, AccountService, IAccount } from 'core/account/account.service';
import { ANY_FIELD_FILTER } from 'core/presentation/anyFieldFilter/anyField.filter';
import { APPLICATION_READ_SERVICE, ApplicationReader, IApplicationSummary } from 'core/application/service/application.read.service';
import { Application } from 'core/application';
import { ICache } from 'core/cache/deckCache.service';
import { OVERRIDE_REGISTRY, OverrideRegistry } from 'core/overrideRegistry/override.registry';
import { VIEW_STATE_CACHE_SERVICE, ViewStateCacheService } from 'core/cache/viewStateCache.service';

import './applications.less';

export interface IApplicationPagination {
  currentPage: number;
  itemsPerPage: number;
  maxSize: number;
}

export class ApplicationsController implements IController {
  private accounts: IAccount[];
  public applications: IApplicationSummary[];
  private applicationsViewStateCache: ICache;
  public filteredApplications: IApplicationSummary[];
  public menuActions: any;
  public applicationsLoaded = false;
  public pagination = this.getDefaultPagination();

  public constructor(
    private $scope: IScope,
    private $uibModal: IModalService,
    private accountService: AccountService,
    private $filter: IFilterService,
    private $state: StateService,
    private applicationReader: ApplicationReader,
    private viewStateCache: ViewStateCacheService,
    private overrideRegistry: OverrideRegistry) {
    'ngInject';

    this.applicationsViewStateCache = this.viewStateCache.get('applications') || this.viewStateCache.createCache('applications', { version: 1 });

    this.accountService.listAccounts().then((accounts) => {
      this.accounts = accounts;
    });

    this.menuActions = [
      {
        displayName: 'Create Application',
        action: () => {
          this.$uibModal.open({
            scope: this.$scope,
            templateUrl: this.overrideRegistry.getTemplate('createApplicationModal', require('./modal/newapplication.html')),
            controller: this.overrideRegistry.getController('CreateApplicationModalCtrl'),
            controllerAs: 'newAppModal'
          }).result.then((app) => this.routeToApplication(app));
        }
      }
    ];

    this.applicationReader.listApplications().then((applications) => {
      applications.forEach((app) => this.fixAccount(app));
      this.applications = applications;
      this.filterApplications();
      this.applicationsLoaded = true;
    });

    this.$scope.$watch('viewState', () => this.cacheViewState(), true);

    this.initializeViewState();
  }

  private cacheViewState(): void {
    this.applicationsViewStateCache.put('#global', this.$scope.viewState);
  }

  private initializeViewState(): void {
    // TODO: Add handlers to the fields in the UI to get rid of $scope...
    this.$scope.viewState = this.applicationsViewStateCache.get('#global') || {
        sortModel: { key: 'name' },
        applicationFilter: '',
      };
  }

  private routeToApplication(app: Application): void {
    this.$state.go(
      'home.applications.application.insight.clusters', {
        application: app.name,
      }
    );
  }

  public filterApplications(): void {
    const q = this.$scope.viewState.applicationFilter;
    const filtered = (this.applications || []).filter(a => {
      const searchable = [a.name, a.email, a.accounts, a.description].filter(f => f).map(f => f.toLowerCase());
      return searchable.some(f => f.includes(q));
    });
    const sorted = this.$filter('orderBy')(filtered, this.$scope.viewState.sortModel.key);
    this.filteredApplications = sorted;
    this.pagination = this.getDefaultPagination();
  };

  public resultPage(): IApplicationSummary[] {
    const pagination = this.pagination,
      allFiltered = this.filteredApplications,
      start = (pagination.currentPage - 1) * pagination.itemsPerPage,
      end = pagination.currentPage * pagination.itemsPerPage;
    if (!allFiltered || !allFiltered.length) {
      return [];
    }
    if (allFiltered.length < pagination.itemsPerPage) {
      return allFiltered;
    }
    if (allFiltered.length < end) {
      return allFiltered.slice(start);
    }
    return allFiltered.slice(start, end);
  };

  private getDefaultPagination(): IApplicationPagination {
    return {
      currentPage: 1,
      itemsPerPage: 12,
      maxSize: 12
    };
  }

  private fixAccount(application: IApplicationSummary): void {
    if (application.accounts) {
      application.accounts = application.accounts.split(',').sort().join(', ');
    }
  }
}

export class ApplicationsComponent implements IComponentOptions {
  public controller: any = ApplicationsController;
  public templateUrl: string = require('../application/applications.html');
}

export const APPLICATIONS_COMPONENT = 'spinnaker.core.applications.component';
module(APPLICATIONS_COMPONENT, [
  require('@uirouter/angularjs').default,
  APPLICATION_READ_SERVICE,
  ACCOUNT_SERVICE,
  ANY_FIELD_FILTER,
  VIEW_STATE_CACHE_SERVICE,
  require('../presentation/sortToggle/sorttoggle.directive'),
  require('../insight/insightmenu.directive'),
  OVERRIDE_REGISTRY,
]).component('applications', new ApplicationsComponent());
