import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ConfirmationModalService } from '../confirmationModal/confirmationModal.service';
import { PagerDutyWriter } from './pagerDuty.write.service';

describe('PagerDuty owner paging', () => {
  it('pages the application owner with the application name and supplied reason', async () => {
    const app = ApplicationModelBuilder.createApplicationForTests('payments');
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve());
    const pageApplicationOwner = vi.spyOn(PagerDutyWriter, 'pageApplicationOwner').mockReturnValue(Promise.resolve());

    const confirmationPromise = PagerDutyWriter.pageApplicationOwnerModal(app);

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(confirmationPromise).toBe(confirm.mock.results.at(-1).value);
    const confirmation = confirm.mock.lastCall[0];
    expect(confirmation).toEqual(
      expect.objectContaining({
        header: 'Page payments Owner',
        buttonText: 'Page Owner',
        askForReason: true,
        reasonRequired: true,
        reasonPlaceholder: 'Why is the owner being paged?',
        taskMonitorConfig: {
          application: app,
          title: 'Paging payments owner',
        },
        submitMethod: expect.any(Function),
      }),
    );
    if (!confirmation) {
      return;
    }

    await confirmation.submitMethod({ reason: '  Production outage  ' });

    expect(pageApplicationOwner).toHaveBeenCalledExactlyOnceWith(app, '[PAYMENTS] Production outage');
  });
});
