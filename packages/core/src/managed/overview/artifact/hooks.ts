import { MarkAsBadActionModal, MarkAsGoodActionModal, PinActionModal, UnpinActionModal } from './ArtifactActionModal';
import {
  FetchPinnedVersionsDocument,
  FetchVersionDocument,
  useMarkVersionAsBadMutation,
  useMarkVersionAsGoodMutation,
  usePinVersionMutation,
  useUnpinVersionMutation,
} from '../../graphql/graphql-sdk';
import { showModal } from '../../../presentation';
import { MODAL_MAX_WIDTH } from '../../utils/defaults';

interface ActionBasePayload {
  application: string;
  environment: string;
  reference: string;
  version: string;
}

export const useUnpinVersion = ({ version, ...payload }: ActionBasePayload, modalTitle: string) => {
  const [onUnpin] = useUnpinVersionMutation({
    variables: { payload: payload },
    refetchQueries: [{ query: FetchPinnedVersionsDocument, variables: { appName: payload.application } }],
  });

  return () => {
    showModal(
      UnpinActionModal,
      {
        application: payload.application,
        environment: payload.environment,
        title: modalTitle,
        actionName: 'Unpin',
        onAction: async () => {
          await onUnpin();
        },
        withComment: false,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};

export const usePinVersion = (payload: ActionBasePayload, modalTitle: string) => {
  const [onPin] = usePinVersionMutation({
    refetchQueries: [{ query: FetchPinnedVersionsDocument, variables: { appName: payload.application } }],
  });

  return () => {
    showModal(
      PinActionModal,
      {
        application: payload.application,
        title: modalTitle,
        actionName: 'Pin',
        onAction: async (comment) => {
          if (!comment) throw new Error('Comment is required');
          await onPin({ variables: { payload: { ...payload, comment } } });
        },
      },

      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};

export const useMarkVersionAsBad = (payload: ActionBasePayload, modalTitle: string) => {
  const [onMarkAsBad] = useMarkVersionAsBadMutation({
    refetchQueries: [
      { query: FetchVersionDocument, variables: { appName: payload.application, versions: [payload.version] } },
    ],
  });

  return () => {
    showModal(
      MarkAsBadActionModal,
      {
        application: payload.application,
        title: modalTitle,
        actionName: 'Mark as Bad',
        onAction: async (comment) => {
          if (!comment) throw new Error('Comment is required');
          await onMarkAsBad({ variables: { payload: { ...payload, comment } } });
        },
      },

      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};

export const useMarkVersionAsGood = (payload: ActionBasePayload, modalTitle: string) => {
  const [onMarkAsBad] = useMarkVersionAsGoodMutation({
    refetchQueries: [
      { query: FetchVersionDocument, variables: { appName: payload.application, versions: [payload.version] } },
    ],
  });

  return () => {
    showModal(
      MarkAsGoodActionModal,
      {
        application: payload.application,
        title: modalTitle,
        actionName: 'Mark as Good',
        onAction: async () => {
          await onMarkAsBad({ variables: { payload: { ...payload } } });
        },
        withComment: false,
      },

      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};
