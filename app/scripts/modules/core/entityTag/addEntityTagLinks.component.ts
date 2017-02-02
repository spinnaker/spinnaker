import {module} from 'angular';
import {IModalService} from 'angular-ui-bootstrap';

import {IEntityTag} from 'core/domain';
import {IEntityRef} from 'core/domain/IEntityTags';
import {ENTITY_TAG_EDITOR_CTRL, EntityTagEditorCtrl, IOwnerOption} from './entityTagEditor.controller';
import {ENTITY_TAGS_HELP} from './entityTags.help';
import {Application} from 'core/application/application.model';

import './entityTagDetails.component.less';

class AddEntityTagLinksCtrl implements ng.IComponentController {
  public application: Application;
  public tagType: string;

  private component: any;
  private entityType: string;
  private onUpdate: () => any;
  private ownerOptions: IOwnerOption[];

  static get $inject() { return ['$uibModal']; }

  public constructor(private $uibModal: IModalService) {}

  public addTag(tagType: string): void {
    this.$uibModal.open({
      templateUrl: require('./entityTagEditor.modal.html'),
      controller: EntityTagEditorCtrl,
      controllerAs: '$ctrl',
      resolve: {
        tag: (): IEntityTag => {
          return {
            name: null,
            value: {
              message: null,
              type: tagType,
            },
          };
        },
        isNew: (): boolean => true,
        owner: (): any => this.component,
        entityType: (): string => this.entityType,
        application: (): Application => this.application,
        entityRef: (): IEntityRef => null,
        onUpdate: (): any => this.onUpdate,
        ownerOptions: (): IOwnerOption[] => this.ownerOptions,
      }
    });
  }
}

class AddEntityTagLinksComponent implements ng.IComponentOptions {
  public bindings: any = {
    component: '<',
    application: '<',
    entityType: '@',
    onUpdate: '&?',
    tagType: '@',
    ownerOptions: '<?',
  };
  public controller: any = AddEntityTagLinksCtrl;
  public template: string = `
    <li role="presentation" class="divider"></li>
    <li>
      <a href ng-click="$ctrl.addTag('notice')">
        Add notice <help-field key="entityTags.{{$ctrl.entityType}}.notice"></help-field>
      </a>
    </li>
    <li>
      <a href ng-click="$ctrl.addTag('alert')">
        Add alert <help-field key="entityTags.{{$ctrl.entityType}}.alert"></help-field>
      </a>
    </li>
  `;
}

export const ADD_ENTITY_TAG_LINKS_COMPONENT = 'spinnaker.core.entityTag.details.component';
module(ADD_ENTITY_TAG_LINKS_COMPONENT, [
  ENTITY_TAG_EDITOR_CTRL,
  ENTITY_TAGS_HELP
])
  .component('addEntityTagLinks', new AddEntityTagLinksComponent());
