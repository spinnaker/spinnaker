import { module } from 'angular';

import { Application, IEntityTag } from 'core';
import { EntityTagEditor, IEntityTagEditorProps, IOwnerOption } from './EntityTagEditor';
import { ENTITY_TAGS_HELP } from './entityTags.help';

class AddEntityTagLinksCtrl implements ng.IComponentController {
  public application: Application;
  public tagType: string;

  private component: any;
  private entityType: string;
  private onUpdate: () => any;
  private ownerOptions: IOwnerOption[];

  public addTag(tagType: string): void {
    const tag: IEntityTag = {
      name: null,
      value: {
        message: null,
        type: tagType,
      },
    };

    const props: IEntityTagEditorProps = {
      tag: tag,
      isNew: true,
      owner: this.component,
      entityType: this.entityType,
      application: this.application,
      entityRef: null,
      onUpdate: this.onUpdate,
      ownerOptions: this.ownerOptions,
    };

    EntityTagEditor.show(props);
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
  public template = `
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
  ENTITY_TAGS_HELP
])
  .component('addEntityTagLinks', new AddEntityTagLinksComponent());
