import { IComponentOptions, module } from 'angular';

const appengineComponentUrlDetailsComponent: IComponentOptions = {
  bindings: { component: '<' },
  template: `
    <dt>HTTPS</dt>
    <dl class="small">
      <a href="{{$ctrl.component.httpsUrl}}" target="_blank">{{$ctrl.component.httpsUrl}}</a>
      <copy-to-clipboard class="copy-to-clipboard copy-to-clipboard-sm"
                         tool-tip="'Copy URL to clipboard'"
                         text="$ctrl.component.httpsUrl"></copy-to-clipboard>
    </dl>
    <dt>HTTP</dt>
    <dl class="small">
      <a href="{{$ctrl.component.httpUrl}}" target="_blank">{{$ctrl.component.httpUrl}}</a>
      <copy-to-clipboard class="copy-to-clipboard copy-to-clipboard-sm"
                         tool-tip="'Copy URL to clipboard'"
                         text="$ctrl.component.httpUrl"></copy-to-clipboard>
    </dl>
  `,
};

export const APPENGINE_COMPONENT_URL_DETAILS = 'spinnaker.appengine.componentUrlDetails.component';

module(APPENGINE_COMPONENT_URL_DETAILS, []).component(
  'appengineComponentUrlDetails',
  appengineComponentUrlDetailsComponent,
);
