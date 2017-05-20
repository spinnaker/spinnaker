import { IScope, module } from 'angular';
import { compact, uniq, map } from 'lodash';
import { Subscription } from 'rxjs';

import { Application } from 'core/application/application.model';
import { IFilterTag } from 'core/filterModel/FilterTags';
import { SECURITY_GROUP_FILTER_MODEL, SecurityGroupFilterModel } from './securityGroupFilter.model';

export const SECURITY_GROUP_FILTER = 'securityGroup.filter.controller';

const ngmodule = module(SECURITY_GROUP_FILTER, [
  require('./securityGroup.filter.service'),
  SECURITY_GROUP_FILTER_MODEL,
  require('core/filterModel/dependentFilter/dependentFilter.service'),
  require('./securityGroupDependentFilterHelper.service'),
]);

export class SecurityGroupFilterCtrl {
  public app: Application;
  public accountHeadings: string[];
  public providerTypeHeadings: string[];
  public regionHeadings: string[];
  public sortFilter: any;
  public stackHeadings: string[];
  public tags: IFilterTag[];
  private groupsUpdatedSubscription: Subscription;
  private locationChangeUnsubscribe: () => void;

  constructor(private securityGroupFilterService: any,
              private SecurityGroupFilterModel: SecurityGroupFilterModel,
              private dependentFilterService: any,
              private securityGroupDependentFilterHelper: any,
              private $scope: IScope,
              private $rootScope: IScope,
  ) {
    'ngInject';
  }

  public $onInit(): void {
    const { $scope, $rootScope, app, SecurityGroupFilterModel, securityGroupFilterService } = this;

    this.sortFilter = SecurityGroupFilterModel.asFilterModel.sortFilter;
    this.tags = SecurityGroupFilterModel.asFilterModel.tags;

    this.groupsUpdatedSubscription = securityGroupFilterService.groupsUpdatedStream
      .subscribe(() => this.tags = SecurityGroupFilterModel.asFilterModel.tags);

    this.initialize();
    app.securityGroups.onRefresh($scope, () => this.initialize());

    this.locationChangeUnsubscribe = $rootScope.$on('$locationChangeSuccess', () => {
      SecurityGroupFilterModel.asFilterModel.activate();
      securityGroupFilterService.updateSecurityGroups(app);
    });

    $scope.$on('$destroy', () => {
      this.groupsUpdatedSubscription.unsubscribe();
      this.locationChangeUnsubscribe();
    });

  }

  private updateSecurityGroups(applyParamsToUrl = true): void {
    const {
      dependentFilterService,
      SecurityGroupFilterModel,
      securityGroupFilterService,
      securityGroupDependentFilterHelper,
      app,
    } = this;

    const { account, region } = dependentFilterService.digestDependentFilters({
      sortFilter: SecurityGroupFilterModel.asFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region'],
      pool: securityGroupDependentFilterHelper.poolBuilder(app.securityGroups.data)
    });

    this.accountHeadings = account;
    this.regionHeadings = region;

    if (applyParamsToUrl) {
      SecurityGroupFilterModel.asFilterModel.applyParamsToUrl();
    }
    securityGroupFilterService.updateSecurityGroups(app);
  };

  private getHeadingsForOption(option: string): string[] {
    return compact(uniq(map(this.app.securityGroups.data, option) as string[])).sort();
  }

  public clearFilters(): void {
    const { securityGroupFilterService } = this;
    securityGroupFilterService.clearFilters();
    securityGroupFilterService.updateSecurityGroups(this.app);
    this.updateSecurityGroups(false);
  }

  private initialize(): void {
    this.stackHeadings = ['(none)'].concat(this.getHeadingsForOption('stack'));
    this.providerTypeHeadings = this.getHeadingsForOption('provider');
    this.updateSecurityGroups();
  }
}

ngmodule.component('securityGroupFilter', {
  templateUrl: require('./securityGroup.filter.component.html'),
  controller: SecurityGroupFilterCtrl,
  bindings: {
    app: '<',
  }
});
