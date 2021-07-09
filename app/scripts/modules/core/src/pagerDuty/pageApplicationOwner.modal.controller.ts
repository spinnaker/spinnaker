import { IController, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';

import { Application } from '../application';
import { PagerDutyWriter } from './pagerDuty.write.service';
import { TaskMonitor } from '../task/monitor/TaskMonitor';

export class PageModalCtrl implements IController {
  public reason: string;
  public taskMonitor: TaskMonitor;

  public static $inject = ['$uibModalInstance', 'application'];
  constructor(public $uibModalInstance: IModalInstanceService, public application: Application) {}

  public submit(): void {
    const taskMonitorConfig = {
      application: this.application,
      title: `Paging ${this.application.name} owner`,
      modalInstance: this.$uibModalInstance,
    };

    const submitMethod = () => {
      const reason = `[${this.application.name.toUpperCase()}] ${this.reason}`;
      return PagerDutyWriter.pageApplicationOwner(this.application, reason);
    };

    this.taskMonitor = new TaskMonitor(taskMonitorConfig);
    this.taskMonitor.submit(submitMethod);
  }
}
export const PAGE_MODAL_CONTROLLER = 'spinnaker.core.pageApplicationOwner.modal.controller';
module(PAGE_MODAL_CONTROLLER, []).controller('PageModalCtrl', PageModalCtrl);
