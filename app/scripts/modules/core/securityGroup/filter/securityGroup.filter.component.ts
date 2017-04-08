import {compact, uniq, map} from 'lodash';
import {IScope, module} from 'angular';
import {Application} from '../../application/application.model';

export const SECURITY_GROUP_FILTER = 'securityGroup.filter.controller';

let ngmodule = module(SECURITY_GROUP_FILTER, [
  require('./securityGroup.filter.service'),
  require('./securityGroup.filter.model'),
  require('../../filterModel/dependentFilter/dependentFilter.service'),
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

  static get $inject() {
    return [
      'securityGroupFilterService',
      'SecurityGroupFilterModel',
      'dependentFilterService',
      'securityGroupDependentFilterHelper',
      '$scope',
      '$rootScope',
    ];
  }

  constructor(private securityGroupFilterService: any,
              private SecurityGroupFilterModel: any,
              private dependentFilterService: any,
              private securityGroupDependentFilterHelper: any,
              private $scope: IScope,
              private $rootScope: IScope,
  ) {
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

  private updateSecurityGroups(): void {
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

    SecurityGroupFilterModel.applyParamsToUrl();
    securityGroupFilterService.updateSecurityGroups(app);
  };

  private getHeadingsForOption(option: string): string[] {
    return compact(uniq(map(this.app.securityGroups.data, option) as string[])).sort();
  }

  public clearFilters(): void {
    const { securityGroupFilterService } = this;
    securityGroupFilterService.clearFilters();
    securityGroupFilterService.updateSecurityGroups(this.app);
    this.updateSecurityGroups();
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
