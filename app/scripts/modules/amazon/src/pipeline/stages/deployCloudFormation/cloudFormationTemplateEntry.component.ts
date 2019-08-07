import { IComponentOptions, IController, IScope, module } from 'angular';
import { yamlDocumentsToString } from '@spinnaker/core';

class CloudFormationTemplateController implements IController {
  public command: any;
  public templateBody: any;
  public rawTemplateBody: string;

  constructor(private $scope: IScope) {
    'ngInject';
  }

  public $onInit = (): void => {
    if (typeof this.command.templateBody === 'string') {
      this.rawTemplateBody = this.command.templateBody;
    } else {
      this.rawTemplateBody = yamlDocumentsToString(this.command.templateBody);
    }
  };

  public handleChange = (rawTemplateBody: string, templateBody: any): void => {
    this.command.templateBody = templateBody || rawTemplateBody;
    this.templateBody = templateBody || rawTemplateBody;
    this.rawTemplateBody = rawTemplateBody;
    this.$scope.$applyAsync();
  };
}

class CloudFormationTemplateEntryComponent implements IComponentOptions {
  public bindings = { command: '<', templateBody: '<' };
  public controller = CloudFormationTemplateController;
  public controllerAs = 'ctrl';
  public template = `
    <yaml-editor
      value="ctrl.rawTemplateBody"
      on-change="ctrl.handleChange"
    ></yaml-editor>`;
}

export const CLOUDFORMATION_TEMPLATE_ENTRY = 'spinnaker.amazon.cloudformation.entry.component';
module(CLOUDFORMATION_TEMPLATE_ENTRY, []).component(
  'cloudFormationTemplateEntry',
  new CloudFormationTemplateEntryComponent(),
);
