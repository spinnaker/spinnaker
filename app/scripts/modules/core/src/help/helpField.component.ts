import { IComponentOptions, IController, module } from 'angular';

export class HelpFieldCtrl implements IController {
  public content: string;
  public expand: boolean;
  public fallback: string;
  public key: string;
  public label: string;
  public placement: string;
}

/**
 * The only purpose of this is to map `key` to `id` so the React version of `HelpField` can be leveraged.
 */
export const helpFieldComponent: IComponentOptions = {
  bindings: {
    key: '@',
    fallback: '@',
    content: '@',
    placement: '@',
    expand: '=',
    label: '@',
  },
  controller: HelpFieldCtrl,
  template: `<help-field-react
              id="$ctrl.key"
              fallback="$ctrl.fallback"
              content="$ctrl.content"
              placement="$ctrl.placement"
              label="$ctrl.label"
              expand="$ctrl.expand"
            ></help-field-react>`,
};

export const HELP_FIELD_COMPONENT = 'spinnaker.core.help.helpField.component';
module(HELP_FIELD_COMPONENT, []).component('helpField', helpFieldComponent);
