import { IComponentOptions, IController, module } from 'angular';
import { IGceAddress } from '../../../address/address.reader';

class GceAddressSelectorCtrl implements IController {
  public selectedAddress: IGceAddress;
  public addressList: IGceAddress[];
  public account: string;
  // The IP address string is the only piece that we can expect a parent resource to keep track of.
  // We'll try to match this IP to a full address map.
  private initialIpAddress: string;

  public $onInit(): void {
    this.selectedAddress = this.addressList.find((address) => address.address === this.initialIpAddress);
    if (!this.selectedAddress) {
      this.selectedAddress = { address: this.initialIpAddress, account: this.account };
    }
  }
}

const gceAddressSelector: IComponentOptions = {
  bindings: {
    initialIpAddress: '<',
    addressList: '<',
    readOnly: '<',
    onAddressSelect: '&',
    account: '<',
  },
  template: `
    <ui-select on-select="$ctrl.onAddressSelect({address: $ctrl.selectedAddress})"
               ng-disabled="$ctrl.readOnly"
               ng-model="$ctrl.selectedAddress"
               class="form-control input-sm">
      <ui-select-match allow-clear>
      {{$ctrl.selectedAddress.address}} <span ng-if="$ctrl.selectedAddress.name">({{$ctrl.selectedAddress.name}})</span>
      </ui-select-match>
      <ui-select-choices repeat="address in $ctrl.addressList | filter: {name: $select.search, account: $ctrl.account}">
        <span>
          {{address.address}} <span ng-if="address.name">({{address.name}})</span> <br>
        </span>
      </ui-select-choices>
    </ui-select>`,
  controller: GceAddressSelectorCtrl,
};

export const GCE_ADDRESS_SELECTOR = 'spinnaker.gce.addressSelector.component';
module(GCE_ADDRESS_SELECTOR, []).component('gceAddressSelector', gceAddressSelector);
