import {module} from 'angular';
import {ENTITY_TAG_WRITER, EntityTagWriter} from './entityTags.write.service';
import {IModalServiceInstance} from 'angular-ui-bootstrap';
import {Application} from '../application/application.model';
import {UUIDGenerator} from '../utils/uuid.service';
import {IEntityTag} from 'core/domain';
import './entityTagEditor.modal.less';
import {TaskMonitorBuilder} from '../task/monitor/taskMonitor.builder';

export class EntityTagEditorCtrl implements ng.IComponentController {

  public taskMonitor: any;

  static get $inject() {
    return ['$uibModalInstance', 'entityTagWriter', 'taskMonitorBuilder', 'owner', 'application', 'entityType',
      'tag', 'onUpdate', 'isNew'];
  }

  public constructor(private $uibModalInstance: IModalServiceInstance,
                     private entityTagWriter: EntityTagWriter,
                     private taskMonitorBuilder: TaskMonitorBuilder,
                     private owner: any,
                     private application: Application,
                     private entityType: string,
                     private tag: IEntityTag,
                     private onUpdate: () => any,
                     public isNew: boolean) {}

  public $onInit(): void {
    this.tag.name = this.tag.name || `spinnaker_ui_${this.tag.value.type}:${UUIDGenerator.generateUuid()}`;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public upsertTag(): void {
    this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: this.application,
      title: `${this.isNew ? 'Create' : 'Update'} ${this.tag.value.type} for ${this.owner.name}`,
      modalInstance: this.$uibModalInstance,
      onTaskComplete: () => this.onUpdate(),
    });

    this.taskMonitor.submit(() => this.entityTagWriter.upsertEntityTag(this.application, this.tag, this.owner, this.entityType, this.isNew));
  }
}

export const ENTITY_TAG_EDITOR_CTRL = 'spinnaker.core.entityTag.editor.controller';
module(ENTITY_TAG_EDITOR_CTRL, [ENTITY_TAG_WRITER])
  .controller('EntityTagEditorCtrl', EntityTagEditorCtrl);
