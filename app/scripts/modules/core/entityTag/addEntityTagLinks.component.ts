import {module} from 'angular';
import {has} from 'lodash';

import {IModalService} from 'angular-ui-bootstrap';

import {IEntityTag} from 'core/domain';
import {ENTITY_TAG_EDITOR_CTRL, EntityTagEditorCtrl} from './entityTagEditor.controller';
import {ENTITY_TAGS_HELP} from './entityTags.help';
import {Application} from 'core/application/application.model';

import './entityTagDetails.component.less';
import {ENTITY_TAG_WRITER, EntityTagWriter} from './entityTags.write.service';

class AddEntityTagLinksCtrl implements ng.IComponentController {
  public tags: IEntityTag[];
  public application: Application;
  public tagType: string;

  private component: any;
  private entityType: string;
  private onUpdate: () => any;

  static get $inject() { return ['$uibModal', 'confirmationModalService', 'entityTagWriter']; }

  public constructor(private $uibModal: IModalService, private confirmationModalService: any,
                     private entityTagWriter: EntityTagWriter) {}

  public $onInit(): void {
    if (this.component.entityTags) {
      this.tags = this.component.entityTags.tags
        .filter((t: IEntityTag) => has(t, 'value.type') && t.value.type === this.tagType)
        .sort((a: IEntityTag, b: IEntityTag) => a.created - b.created);
    }
  }

  public $onChanges(): void {
    this.$onInit();
  }

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
        onUpdate: (): any => this.onUpdate,
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
  ENTITY_TAG_WRITER,
  ENTITY_TAGS_HELP
])
  .component('addEntityTagLinks', new AddEntityTagLinksComponent());
