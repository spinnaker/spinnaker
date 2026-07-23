import * as ngimport from 'ngimport';

import { ReactModal } from '../presentation/ReactModal';
import { ConfirmationModalService } from './confirmationModal.service';

describe('ConfirmationModalService', () => {
  let originalQ: typeof ngimport.$q;

  beforeEach(() => {
    originalQ = ngimport.$q;
  });

  afterEach(() => {
    (ngimport as any).$q = originalQ;
  });

  it('uses the direct $q fallback when Angular has not been bootstrapped', async () => {
    (ngimport as any).$q = undefined;
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve('confirmed'));

    const result = await ConfirmationModalService.confirm({ header: 'Rollout restart' });

    expect(result).toBe('confirmed');
    expect(ReactModal.show).toHaveBeenCalled();
  });
});
