import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ConfirmationModalService } from '../confirmationModal/confirmationModal.service';
import { PagerDutyWriter } from './pagerDuty.write.service';

describe('PagerDuty owner paging', () => {
  it('pages the application owner with the application name and supplied reason', async () => {
    const app = ApplicationModelBuilder.createApplicationForTests('payments');
    const confirm = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve());
    const pageApplicationOwner = spyOn(PagerDutyWriter, 'pageApplicationOwner').and.returnValue(Promise.resolve());

    const confirmationPromise = PagerDutyWriter.pageApplicationOwnerModal(app);

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(confirmationPromise).toBe(confirm.calls.mostRecent().returnValue);
    const confirmation = confirm.calls.mostRecent()?.args[0];
    expect(confirmation).toEqual(
      jasmine.objectContaining({
        header: 'Page payments Owner',
        buttonText: 'Page Owner',
        askForReason: true,
        reasonRequired: true,
        reasonPlaceholder: 'Why is the owner being paged?',
        taskMonitorConfig: {
          application: app,
          title: 'Paging payments owner',
        },
        submitMethod: jasmine.any(Function),
      }),
    );
    if (!confirmation) {
      return;
    }

    await confirmation.submitMethod({ reason: '  Production outage  ' });

    expect(pageApplicationOwner).toHaveBeenCalledOnceWith(app, '[PAYMENTS] Production outage');
  });
});
