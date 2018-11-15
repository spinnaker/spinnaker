import { IComponentOptions, module } from 'angular';
export class AccountRegionClusterSelectorWrapperComponent implements IComponentOptions {
  public bindings: any = {
    application: '<',
    component: '<',
    accounts: '<',
    clusterField: '<',
    singleRegion: '<',
    showAllRegions: '<?',
    onAccountUpdate: '<?',
    disableRegionSelect: '<?',
  };
  public template = `
    <account-region-cluster-selector
      application="$ctrl.application"
      component="$ctrl.component"
      accounts="$ctrl.accounts"
      cluster-field="{{::$ctrl.clusterField}}"
      single-region="$ctrl.singleRegion"
      show-all-regions="$ctrl.showAllRegions"
      on-account-update="$ctrl.onAccountUpdate"
      disable-region-select="$ctrl.disableRegionSelect">
    </account-region-cluster-selector>
  `;
}
export const ACCOUNT_REGION_CLUSTER_SELECTOR_WRAPPER = 'spinnaker.core.accountRegionClusterSelectorWrapper.component';
module(ACCOUNT_REGION_CLUSTER_SELECTOR_WRAPPER, []).component(
  'accountRegionClusterSelectorWrapper',
  new AccountRegionClusterSelectorWrapperComponent(),
);
