import React from 'react';

import { ResumeManagementModal } from './Configuration';
import { useFetchApplicationManagementStatusQuery, useToggleManagementMutation } from '../graphql/graphql-sdk';
import { MessageBox, MessagesSection } from '../messages/MessageBox';
import { showModal } from '../../presentation';
import { MODAL_MAX_WIDTH } from '../utils/defaults';

export const ManagementWarning = ({ appName }: { appName: string }) => {
  const { data, refetch } = useFetchApplicationManagementStatusQuery({ variables: { appName } });
  const [toggleManagement] = useToggleManagementMutation();

  const onClick = React.useCallback(() => {
    showModal(
      ResumeManagementModal,
      {
        application: appName,
        onAction: async () => {
          await toggleManagement({ variables: { application: appName, isPaused: false } });
          refetch();
        },
        logCategory: 'App::Management',
        onSuccess: refetch,
        withComment: false,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  }, [appName, refetch]);

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
