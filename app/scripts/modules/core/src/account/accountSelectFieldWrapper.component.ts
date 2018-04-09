import { IComponentOptions, module } from 'angular';

export class AccountSelectFieldWrapperComponent implements IComponentOptions {
  public bindings: any = {
    accounts: '<',
    component: '<',
    field: '<',
    provider: '<',
    loading: '<',
    onChange: '<',
    labelColumns: '<',
    readOnly: '<',
    multiselect: '<',
  };
  public template = `
    <account-select-field
      accounts="$ctrl.accounts"
      component="$ctrl.component"
      field="{{::$ctrl.field}}"
      provider="$ctrl.provider"
      loading="$ctrl.loading"
      on-change="$ctrl.onChange($ctrl.component[$ctrl.field])"
      label-columns="{{::$ctrl.labelColumns}}"
      read-only="$ctrl.readOnly"
      multiselect="$ctrl.multiselect">
    </account-select-field>
  `;
}

export const ACCOUNT_SELECT_FIELD_WRAPPER = 'spinnaker.core.account.accountSelectFieldWrapper.component';
module(ACCOUNT_SELECT_FIELD_WRAPPER, []).component(
  'accountSelectFieldWrapper',
  new AccountSelectFieldWrapperComponent(),
);
