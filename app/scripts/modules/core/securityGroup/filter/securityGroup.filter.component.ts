import {compact, uniq, map} from 'lodash';
import {IScope, module} from 'angular';

import {Application} from 'core/application/application.model';
import {SECURITY_GROUP_FILTER_MODEL} from './securityGroupFilter.model';

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
  public tags: any[];

  constructor(private securityGroupFilterService: any,
              private SecurityGroupFilterModel: any,
              private dependentFilterService: any,
              private securityGroupDependentFilterHelper: any,
              private $scope: IScope,
              private $rootScope: IScope,
  ) {
    'ngInject';
  }

  public $onInit(): void {
    const { $scope, $rootScope, app, SecurityGroupFilterModel, securityGroupFilterService } = this;

    this.sortFilter = SecurityGroupFilterModel.sortFilter;
    this.tags = SecurityGroupFilterModel.tags;

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      SecurityGroupFilterModel.activate();
      securityGroupFilterService.updateSecurityGroups(app);
    }));

    this.initialize();
    app.securityGroups.onRefresh($scope, () => this.initialize());
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
      sortFilter: SecurityGroupFilterModel.sortFilter,
      dependencyOrder: ['providerType', 'account', 'region'],
      pool: securityGroupDependentFilterHelper.poolBuilder(app.securityGroups.data)
    });

    this.accountHeadings = account;
    this.regionHeadings = region;

    if (applyParamsToUrl) {
      SecurityGroupFilterModel.applyParamsToUrl();
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
