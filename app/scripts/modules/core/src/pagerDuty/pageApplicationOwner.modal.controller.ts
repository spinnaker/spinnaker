import { module, IController } from 'angular';

import { Application } from 'core/application';
import { APPLICATION_WRITE_SERVICE } from 'core/application/service/application.write.service';
import { TaskMonitor } from 'core/task/monitor/taskMonitor';
import { PagerDutyWriter } from './pagerDuty.write.service';
import { IModalInstanceService } from 'angular-ui-bootstrap';

export class PageModalCtrl implements IController {
  public reason: string;
  public taskMonitor: TaskMonitor;

  constructor(
    public $uibModalInstance: IModalInstanceService,
    public pagerDutyWriter: PagerDutyWriter,
    public application: Application,
  ) {
    'ngInject';
  }

  public submit(): void {
    const taskMonitorConfig = {
      application: this.application,
      title: `Paging ${this.application.name} owner`,
      modalInstance: this.$uibModalInstance,
    };

    const submitMethod = () => {
      const reason = `[${this.application.name.toUpperCase()}] ${this.reason}`;
      return this.pagerDutyWriter.pageApplicationOwner(this.application, reason);
    };

    this.taskMonitor = new TaskMonitor(taskMonitorConfig);
    this.taskMonitor.submit(submitMethod);
  }
}
export const PAGE_MODAL_CONTROLLER = 'spinnaker.core.pageApplicationOwner.modal.controller';
module(PAGE_MODAL_CONTROLLER, [APPLICATION_WRITE_SERVICE]).controller('PageModalCtrl', PageModalCtrl);
