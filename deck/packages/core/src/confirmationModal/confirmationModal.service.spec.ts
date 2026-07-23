import { ReactModal } from '../presentation/ReactModal';
import { ConfirmationModalService } from './confirmationModal.service';

describe('ConfirmationModalService', () => {
  it('resolves the direct modal result', async () => {
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve('confirmed'));

    const result = await ConfirmationModalService.confirm({ header: 'Rollout restart' });

    expect(result).toBe('confirmed');
    expect(ReactModal.show).toHaveBeenCalled();
  });
});
