import {Component, Output, EventEmitter} from '@angular/core';

import {IDowngradeItem} from 'core/domain/IDowngradeItem';
@Component({
  selector: 'deck-modal-close',
  template: `
    <div class="close-button pull-right">
      <a href class="btn btn-link" (click)="close($event)" >
        <span class="glyphicon glyphicon-remove"></span>
      </a>
    </div>
`
})
export class ModalCloseComponent {

  @Output()
  public dismiss = new EventEmitter();

  public close($event: ng.IAngularEvent) {
    $event.preventDefault();
    this.dismiss.emit();
  }
}

export const MODAL_CLOSE_COMPONENT = 'spinnaker.core.modal.modalClose.directive';
export const MODAL_CLOSE_COMPONENT_DOWNGRADE: IDowngradeItem = {
  moduleName: MODAL_CLOSE_COMPONENT,
  injectionName: 'modalClose',
  moduleClass: ModalCloseComponent,
  outputs: ['dismiss']
};
