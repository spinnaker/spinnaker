import classnames from 'classnames';
import React from 'react';
import './MessageBox.less';

interface IMessageBoxProps {
  type?: 'SUCCESS' | 'INFO' | 'WARNING' | 'ERROR';
  onDismiss?: () => void;
}

const typeToClassName: { [key in Required<IMessageBoxProps>['type']]?: string } = {
  ERROR: 'fas fa-times',
  WARNING: 'fas fa-exclamation',
};

export const MessageBox: React.FC<IMessageBoxProps> = ({ children, type, onDismiss }) => {
  return (
    <div className={classnames('MessageBox sp-padding-m', type?.toLowerCase())}>
      {type && (
        <div>
          <i className={classnames(typeToClassName[type], 'message-icon')} />
        </div>
      )}
      <div>{children}</div>
      {onDismiss && (
        <button className="as-link dismiss sp-margin-m-left" onClick={onDismiss}>
          Dismiss
        </button>
      )}
    </div>
  );
};

interface IMessagesSectionProps {
  sticky?: boolean;
}

export const MessagesSection: React.FC<IMessagesSectionProps> = ({ children, sticky }) => {
  return <div className={classnames('MessagesSection', { sticky })}>{children}</div>;
};
