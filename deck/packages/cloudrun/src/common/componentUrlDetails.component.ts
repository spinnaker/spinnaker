import type { IComponentOptions } from 'angular';
import { module } from 'angular';

const cloudrunComponentUrlDetailsComponent: IComponentOptions = {
  bindings: { component: '<' },
  template: `
    <dt>HTTPS</dt>
    <dl class="small">
      <a href="{{$ctrl.component.url}}" target="_blank">{{$ctrl.component.url}}</a>
      <copy-to-clipboard class="copy-to-clipboard copy-to-clipboard-sm"
                         tool-tip="'Copy URL to clipboard'"
                         text="$ctrl.component.httpsUrl"></copy-to-clipboard>
    </dl>
  `,
};

export const CLOUDRUN_COMPONENT_URL_DETAILS = 'spinnaker.cloudrun.componentUrlDetails.component';

module(CLOUDRUN_COMPONENT_URL_DETAILS, []).component(
  'cloudrunComponentUrlDetails',
  cloudrunComponentUrlDetailsComponent,
);
