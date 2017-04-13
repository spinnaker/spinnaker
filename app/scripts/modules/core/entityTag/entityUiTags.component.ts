import {module} from 'angular';
import {IEntityTag} from 'core/domain';
import './entityUiTags.component.less';
import {Application} from '../application/application.model';
import {EntityTagWriter} from './entityTags.write.service';
import {IModalService} from 'angular-ui-bootstrap';
import {EntityTagEditorCtrl} from './entityTagEditor.controller';

import './entityUiTags.popover.less';
import {IEntityRef} from '../domain/IEntityTags';

class EntityUiTagsCtrl implements ng.IComponentController {

  public application: Application;
  public entityType: string;
  public pageLocation: string; // for tracking
  public popoverTemplate: string = require('./entityUiTags.popover.html');
  public popoverType: string;
  public displayPopover: boolean;
  public popoverContents: IEntityTag[] = [];
  public alertAnalyticsLabel: string;
  public noticeAnalyticsLabel: string;
  private popoverClose: ng.IPromise<void>;
  private onUpdate: () => any;

  private component: any;

  static get $inject() { return ['$timeout', '$uibModal', 'confirmationModalService', 'entityTagWriter']; }

  public constructor(private $timeout: ng.ITimeoutService, private $uibModal: IModalService,
                     private confirmationModalService: any, private entityTagWriter: EntityTagWriter) {}

  public $onDestroy(): void {
    if (this.popoverClose) {
      this.$timeout.cancel(this.popoverClose);
    }
  }

  public $onInit(): void {
    if (!this.component.entityTags) {
      return;
    }
    this.alertAnalyticsLabel = [
      this.pageLocation,
      this.entityType,
      this.component.entityTags.entityRef.account,
      this.component.entityTags.entityRef.region,
      this.component.entityTags.entityRef.entityId,
      this.component.entityTags.entityRef.region,
      this.component.entityTags.alerts.map((a: IEntityTag) => a.name).join(',')
    ].join(':');

    this.noticeAnalyticsLabel = [
      this.pageLocation,
      this.entityType,
      this.component.entityTags.entityRef.account,
      this.component.entityTags.entityRef.region,
      this.component.entityTags.entityRef.entityId,
      this.component.entityTags.notices.map((a: IEntityTag) => a.name).join(',')
    ].join(':');
  }

  public deleteTag(tag: IEntityTag): void {
    const taskMonitorConfig: any = {
      application: this.application,
      title: `Deleting ${tag.value['type']} on ${this.component.name}`,
      onTaskComplete: () => this.onUpdate(),
    };

    this.confirmationModalService.confirm({
      header: `Really delete ${tag.value['type']}?`,
      buttonText: `Delete ${tag.value['type']}`,
      provider: this.component.cloudProvider,
      account: this.component.account,
      applicationName: this.application.name,
      taskMonitorConfig: taskMonitorConfig,
      submitMethod: () => this.entityTagWriter.deleteEntityTag(this.application, this.component,
        this.component.entityTags, tag.name)
    });
  }

  public editTag(tag: IEntityTag) {
    this.$uibModal.open({
      templateUrl: require('./entityTagEditor.modal.html'),
      controller: EntityTagEditorCtrl,
      controllerAs: '$ctrl',
      resolve: {
        tag: (): IEntityTag => {
          return {
            name: tag.name,
            value: {
              message: tag.value['message'],
              type: tag.value['type']
            }
          };
        },
        isNew: (): boolean => false,
        owner: (): any => this.component,
        entityType: (): string => this.entityType,
        application: (): Application => this.application,
        onUpdate: (): any => this.onUpdate,
        ownerOptions: (): any => null,
        entityRef: (): IEntityRef => this.component.entityTags.entityRef,
      }
    });
  }


  // Popover bits allow the popover to stay open when hovering to allow users to click on links, highlight text, etc.
  // We may end up extracting this into a common widget if we want to use it elsewhere

  public showPopover(type: string): void {
    this.popoverType = type;
    this.popoverContents = type === 'alert' ? this.component.entityTags.alerts : this.component.entityTags.notices;
    this.displayPopover = true;
    this.popoverHovered();
  }

  public popoverHovered(): void {
    if (this.popoverClose) {
      this.$timeout.cancel(this.popoverClose);
      this.popoverClose = null;
    }
  }

  public hidePopover(defer: boolean): void {
    const hidePopoverType: string = this.popoverType;
    if (defer) {
      this.popoverClose = this.$timeout(() => {
        if (this.popoverType === hidePopoverType) {
          this.displayPopover = false;
        }
      }, 500);
    } else {
      this.displayPopover = false;
    }
  }
}

class EntityUiTagsComponent implements ng.IComponentOptions {
  public bindings: any = {
    component: '<',
    application: '<',
    pageLocation: '@',
    onUpdate: '&?',
    entityType: '@',
  };
  public controller: any = EntityUiTagsCtrl;
  public template = `
    <span ng-if="$ctrl.component.entityTags.alerts.length + $ctrl.component.entityTags.notices.length > 0">
      <span ng-if="$ctrl.component.entityTags.alerts.length > 0"
            class="tag-marker" 
            ng-mouseover="$ctrl.showPopover('alert')" 
            ng-mouseleave="$ctrl.hidePopover(true)">
        <span uib-popover-template="$ctrl.popoverTemplate"
              popover-placement="auto top"
              popover-trigger="'none'"
              analytics-on="mouseenter"
              analytics-category="Notice hovered"
              analytics-label="{{$ctrl.alertAnalyticsLabel}}"
              popover-is-open="$ctrl.popoverType === 'alert' && $ctrl.displayPopover"
              popover-class="no-padding">
          <i class="entity-tag fa fa-exclamation-triangle"></i>
        </span>
      </span>
      <span ng-if="$ctrl.component.entityTags.notices.length > 0"
            class="tag-marker"
            ng-mouseover="$ctrl.showPopover('notice')" 
            ng-mouseleave="$ctrl.hidePopover(true)">
        <span uib-popover-template="$ctrl.popoverTemplate"
              popover-placement="auto top"
              popover-trigger="'none'"
              analytics-on="mouseenter"
              analytics-category="Notice hovered"
              analytics-label="{{$ctrl.noticeAnalyticsLabel}}"
              popover-is-open="$ctrl.popoverType === 'notice' && $ctrl.displayPopover"
              popover-class="no-padding">
          <i class="entity-tag fa fa-flag"></i>
        </span>
      </span>
    </span>
  `;
}

export const ENTITY_UI_TAGS_COMPONENT = 'spinnaker.core.entityTags.uiTags.component';
module(ENTITY_UI_TAGS_COMPONENT, [])
  .component('entityUiTags', new EntityUiTagsComponent());
