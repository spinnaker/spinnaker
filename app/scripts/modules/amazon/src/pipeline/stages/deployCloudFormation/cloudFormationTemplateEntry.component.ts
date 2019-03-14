import { IComponentOptions, IController, IScope, module } from 'angular';

class CloudFormationTemplateController implements IController {
  public command: any;
  public templateBody: any;
  public rawTemplateBody: string;

  constructor(private $scope: IScope) {
    'ngInject';
  }

  public $onInit = (): void => {
    this.rawTemplateBody = JSON.stringify(this.command.templateBody, null, 2);
  };

  public handleChange = (rawTemplateBody: string, templateBody: any): void => {
    this.command.templateBody = templateBody;
    this.templateBody = templateBody;
    this.rawTemplateBody = rawTemplateBody;
    this.$scope.$applyAsync();
  };
}

class CloudFormationTemplateEntryComponent implements IComponentOptions {
  public bindings = { command: '<', templateBody: '<' };
  public controller = CloudFormationTemplateController;
  public controllerAs = 'ctrl';
  public template = `
    <json-editor
      value="ctrl.rawTemplateBody"
      on-change="ctrl.handleChange"
    ></json-editor>`;
}

export const CLOUDFORMATION_TEMPLATE_ENTRY = 'spinnaker.amazon.cloudformation.entry.component';
module(CLOUDFORMATION_TEMPLATE_ENTRY, []).component(
  'cloudFormationTemplateEntry',
  new CloudFormationTemplateEntryComponent(),
);
