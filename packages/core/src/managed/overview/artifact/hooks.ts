import type { IVersionActionsProps } from './ArtifactActionModal';
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

export const useUnpinVersion = (payload: IVersionActionsProps) => {
  const {
    application,
    environment,
    reference,
    selectedVersion: { buildNumber, commitMessage },
  } = payload;
  const [onUnpin] = useUnpinVersionMutation({
    variables: { payload: { application, environment, reference } },
    refetchQueries: [{ query: FetchPinnedVersionsDocument, variables: { appName: application } }],
  });

  return () => {
    showModal(
      UnpinActionModal,
      {
        title: [`Unpin #${buildNumber}`, commitMessage].filter(Boolean).join(' - '),
        actionName: 'Unpin',
        onAction: async () => {
          await onUnpin();
        },
        withComment: false,
        actionProps: payload,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};

export const usePinVersion = (payload: IVersionActionsProps) => {
  const {
    application,
    environment,
    isCurrent,
    version,
    reference,
    selectedVersion: { buildNumber },
  } = payload;
  const [onPin] = usePinVersionMutation({
    refetchQueries: [{ query: FetchPinnedVersionsDocument, variables: { appName: payload.application } }],
  });

  return () => {
    showModal(
      PinActionModal,
      {
        title: isCurrent
          ? `Pin #${buildNumber}`
          : `Roll back to #${buildNumber} and pin to ${environment.toUpperCase()}`,
        actionName: isCurrent ? 'Pin' : 'Roll back',
        onAction: async (comment) => {
          if (!comment) throw new Error('Comment is required');
          await onPin({ variables: { payload: { application, environment, reference, version, comment } } });
        },
        actionProps: payload,
      },

      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};

export const useMarkVersionAsBad = (payload: IVersionActionsProps) => {
  const {
    application,
    environment,
    reference,
    version,
    isCurrent,
    selectedVersion: { buildNumber },
  } = payload;
  const [onMarkAsBad] = useMarkVersionAsBadMutation({
    refetchQueries: [{ query: FetchVersionDocument, variables: { appName: payload.application, versions: [version] } }],
  });

  return () => {
    showModal(
      MarkAsBadActionModal,
      {
        title:
          (isCurrent ? `Roll back ${environment.toUpperCase()} to previous version and reject ` : 'Reject') +
          ` #${buildNumber}`,
        actionName: 'Rollback',
        onAction: async (comment) => {
          if (!comment) throw new Error('Comment is required');
          await onMarkAsBad({ variables: { payload: { application, environment, reference, version, comment } } });
        },
        actionProps: payload,
        isCurrent,
      },

      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};

export const useMarkVersionAsGood = (payload: IVersionActionsProps) => {
  const {
    application,
    environment,
    reference,
    version,
    selectedVersion: { buildNumber, commitMessage },
  } = payload;
  const [onMarkAsBad] = useMarkVersionAsGoodMutation({
    refetchQueries: [{ query: FetchVersionDocument, variables: { appName: payload.application, versions: [version] } }],
  });

  return () => {
    showModal(
      MarkAsGoodActionModal,
      {
        title: [`Allow deploying #${buildNumber}`, commitMessage].filter(Boolean).join(' - '),
        actionName: 'Allow',
        onAction: async () => {
          await onMarkAsBad({ variables: { payload: { application, environment, reference, version } } });
        },
        withComment: false,
        actionProps: payload,
      },

      { maxWidth: MODAL_MAX_WIDTH },
    );
  };
};
