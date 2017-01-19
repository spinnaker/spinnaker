import {module} from 'angular';
import {ENTITY_TAG_WRITER, EntityTagWriter} from './entityTags.write.service';
import {IModalServiceInstance} from 'angular-ui-bootstrap';
import {Application} from 'core/application/application.model';
import {UUIDGenerator} from 'core/utils/uuid.service';
import {IEntityTag} from 'core/domain';
import {IEntityRef} from 'core/domain/IEntityTags';
import {EntityRefBuilder} from './entityRef.builder';
import {TaskMonitorBuilder} from 'core/task/monitor/taskMonitor.builder';

import './entityTagEditor.modal.less';

export interface IOwnerOption {
  label: string;
  type: string;
  owner: any;
  isDefault: boolean;
}

export class EntityTagEditorCtrl implements ng.IComponentController {

  public taskMonitor: any;

  static get $inject() {
    return ['$uibModalInstance', 'entityTagWriter', 'taskMonitorBuilder', 'owner', 'application', 'entityType',
      'tag', 'onUpdate', 'ownerOptions', 'entityRef', 'isNew'];
  }

  public constructor(private $uibModalInstance: IModalServiceInstance,
                     private entityTagWriter: EntityTagWriter,
                     private taskMonitorBuilder: TaskMonitorBuilder,
                     private owner: any,
                     private application: Application,
                     private entityType: string,
                     private tag: IEntityTag,
                     private onUpdate: () => any,
                     public ownerOptions: IOwnerOption[],
                     private entityRef: IEntityRef,
                     public isNew: boolean) {}

  public $onInit(): void {
    if (this.ownerOptions && this.ownerOptions.length) {
      this.owner = this.ownerOptions[0].owner;
      this.ownerChanged(this.ownerOptions[0]);
    }
    this.tag.name = this.tag.name || `spinnaker_ui_${this.tag.value.type}:${UUIDGenerator.generateUuid()}`;
  }

  public ownerChanged(option: IOwnerOption): void {
    this.entityType = option.type;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public upsertTag(): void {
    const entityRef: IEntityRef = this.entityRef || EntityRefBuilder.getBuilder(this.entityType)(this.owner);

    this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: this.application,
      title: `${this.isNew ? 'Create' : 'Update'} ${this.tag.value.type} for ${entityRef.entityId}`,
      modalInstance: this.$uibModalInstance,
      onTaskComplete: () => this.onUpdate(),
    });

    this.taskMonitor.submit(() => this.entityTagWriter.upsertEntityTag(this.application, this.tag, entityRef, this.isNew));
  }
}

export const ENTITY_TAG_EDITOR_CTRL = 'spinnaker.core.entityTag.editor.controller';
module(ENTITY_TAG_EDITOR_CTRL, [ENTITY_TAG_WRITER])
  .controller('EntityTagEditorCtrl', EntityTagEditorCtrl);
