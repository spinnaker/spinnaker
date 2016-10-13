import {module} from 'angular';

import modalWizardServiceModule from './v2modalWizard.service';
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

export interface WizardPageState {
  rendered: boolean;
  done: boolean;
  dirty: boolean;
  required: boolean;
  markCompleteOnView: boolean;
}

export class WizardPageController implements ng.IComponentController {
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
  public state: WizardPageState;

  /**
   * Offset to add to the heading when making them sticky
   * @type {number}
   */
  public pageOffset: number = 0;

  static get $inject() { return ['$scope', 'v2modalWizardService']; }

  public constructor(private $scope: ng.IScope, private v2modalWizardService: any) {}

  $onInit() {
    this.render = this.render !== false;
    this.markCompleteOnView = this.markCompleteOnView !== false;

    this.state = {
      rendered: this.render,
      done: this.done || !this.mandatory,
      dirty: false,
      required: this.mandatory,
      markCompleteOnView: this.markCompleteOnView
    };
    this.pageOffset = this.v2modalWizardService.pageOffset;
    this.v2modalWizardService.registerPage(this.key, this.label, this.state);
    this.$scope.$on('$destroy', () => this.v2modalWizardService.setRendered(this.key, false));
  }
}

class WizardPageComponent implements ng.IComponentOptions {
  public bindings: any = {
    mandatory: '<',
    done: '<',
    markCompleteOnView: '<',
    key: '@',
    label: '@',
    render: '<',
  };
  public transclude: boolean = true;
  public templateUrl: string = require('./v2wizardPage.component.html');
  public controller: ng.IComponentController = WizardPageController;
}

const moduleName = 'spinnaker.core.modal.wizard.wizardPage.component';

module(moduleName, [
  modalWizardServiceModule,
]).component('v2WizardPage', new WizardPageComponent());

export default moduleName;
