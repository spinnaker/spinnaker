import { IController, module } from 'angular';
import { AuthenticationService } from 'core/authentication';

class PipelineRolesController implements IController {
  public pipeline: any;
  public allowedRoles: string[];

  public $onInit(): void {
    this.allowedRoles = AuthenticationService.getAuthenticatedUser().roles;
    this.pipeline.roles = this.pipeline.roles || [];
  }
}

class PipelineRolesComponent implements ng.IComponentOptions {
  public template = `
      <div class="form-group row">
        <div class="col-md-10">
          <div class="row">
            <label class="col-md-3 sm-label-right">
              Permissions
              <help-field key="pipeline.config.roles.help"></help-field>
            </label>
            <div class="col-md-9">
              <ui-select multiple ng-model="rolesCtrl.pipeline.roles" class="form-control input-sm">
                <ui-select-match>
                  {{$item}}
                </ui-select-match>
                <ui-select-choices repeat="role in rolesCtrl.allowedRoles | filter: $select.search ">
                  <span ng-bind-html="role | highlight: $select.search"></span>
                </ui-select-choices>
              </ui-select>
            </div>
          </div>
        </div>
    </div>
 `;

  public controller = PipelineRolesController;
  public controllerAs = 'rolesCtrl';
  public bindings = {
    pipeline: '=',
  };
}

export const PIPELINE_ROLES_COMPONENT = 'spinnaker.core.pipeline.roles.component';
module(PIPELINE_ROLES_COMPONENT, []).component('pipelineRoles', new PipelineRolesComponent());
