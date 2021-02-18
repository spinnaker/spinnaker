import { IController, module } from 'angular';

import { IModalWizardPageState, ModalWizard } from './ModalWizard';
/**
 * Wizard page directive
 * possible attributes:
 *   - key (required): Any string value, unique within the wizard; it becomes the the hook to access the page state
 *     through the wizard, e.g. wizard.getPage('page-1').markComplete()
 *   - label (required): Any string value; it becomes label in the wizard flow
 *   - done (optional, default: false): when set to true, the page will be marked complete when rendered
 *   - mandatory (optional, default: true): when set to false, the wizard will not consider this page when isComplete
 *     is called
 *   - render (optional, default: true): when set to false, registers the page with the wizard, but does not participate
 *     in the wizard flow. To add the page to the flow, call wizard.includePage(key)
 *   - markCompleteOnView (optional, default: true): when set to false, the page will not be marked complete when
 *     scrolled into view
 */

export class WizardPageController implements IController {
  /**
   * when set to false, the wizard will not consider this page when isComplete is called
   * default: false
   * @type {boolean}
   */
  public mandatory: boolean;

  /**
   * when set to true, the page will be marked complete when rendered
   * default: false
   * @type {boolean}
   */
  public done: boolean;

  /**
   * when set to false, the page will not be marked clean when scrolled into view
   * default: true
   * @type {boolean}
   */
  public markCleanOnView: boolean;

  /**
   * when set to false, the page will not be marked complete when scrolled into view
   * default: true
   * @type {boolean}
   */
  public markCompleteOnView: boolean;

  /**
   * Any string value, unique within the wizard; it becomes the the hook to access the page state through the wizard,
   * e.g. wizard.getPage('page-1').markComplete()
   */
  public key: string;

  /**
   * Any string value; it becomes label in the wizard flow
   */
  public label: string;

  /**
   * when set to false, registers the page with the wizard, but does not participate in the wizard flow.
   * To add the page to the flow, call wizard.includePage(key)
   * default: true
   * @type {boolean}
   */
  public render: boolean;

  /**
   * Internal state of the page, initialized based on other public fields
   */
  public state: IModalWizardPageState;

  public static $inject = ['$scope'];
  public constructor(private $scope: ng.IScope) {}

  public $onInit() {
    this.render = this.render !== false;
    this.markCleanOnView = this.markCleanOnView !== false;
    this.markCompleteOnView = this.markCompleteOnView !== false;

    this.state = {
      blocked: false,
      current: false,
      rendered: this.render,
      done: this.done || !this.mandatory,
      dirty: false,
      required: this.mandatory,
      markCleanOnView: this.markCleanOnView,
      markCompleteOnView: this.markCompleteOnView,
    };
    ModalWizard.registerPage(this.key, this.label, this.state);
    this.$scope.$on('$destroy', () => ModalWizard.setRendered(this.key, false));
  }
}

const wizardPageComponent: ng.IComponentOptions = {
  bindings: {
    mandatory: '<',
    done: '<',
    markCleanOnView: '<',
    markCompleteOnView: '<',
    key: '@',
    label: '@',
    render: '<',
  },
  transclude: true,
  templateUrl: require('./v2wizardPage.component.html'),
  controller: WizardPageController,
};

export const V2_WIZARD_PAGE_COMPONENT = 'spinnaker.core.modal.wizard.wizardPage.component';
module(V2_WIZARD_PAGE_COMPONENT, []).component('v2WizardPage', wizardPageComponent);
