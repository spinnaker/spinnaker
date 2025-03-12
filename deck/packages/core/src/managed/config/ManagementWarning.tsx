import React from 'react';

import { ResumeManagementModal } from './Configuration';
import {
  FetchApplicationManagementStatusDocument,
  useFetchApplicationManagementStatusQuery,
  useToggleManagementMutation,
} from '../graphql/graphql-sdk';
import { MessageBox, MessagesSection } from '../messages/MessageBox';
import { showModal } from '../../presentation';
import { MODAL_MAX_WIDTH } from '../utils/defaults';

export const ManagementWarning = ({ appName }: { appName: string }) => {
  const { data } = useFetchApplicationManagementStatusQuery({ variables: { appName } });
  const [toggleManagement] = useToggleManagementMutation({
    refetchQueries: [{ query: FetchApplicationManagementStatusDocument, variables: { appName } }],
  });

  const onClick = React.useCallback(() => {
    showModal(
      ResumeManagementModal,
      {
        application: appName,
        onAction: async () => {
          await toggleManagement({ variables: { application: appName, isPaused: false } });
        },
        logCategory: 'App::Management',
        withComment: false,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  }, [appName]);

  if (data?.application?.isPaused) {
    return (
      <MessagesSection sticky>
        <MessageBox type="WARNING">
          Application management is disabled.{' '}
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              onClick();
            }}
          >
            Click here to enable
          </a>
        </MessageBox>
      </MessagesSection>
    );
  }
  return null;
};
