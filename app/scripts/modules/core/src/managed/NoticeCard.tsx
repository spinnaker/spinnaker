import React from 'react';
import classNames from 'classnames';

import { Icon, IconNames } from '../presentation';

import styles from './NoticeCard.module.css';

interface INoticeCardProps {
  title: React.ReactNode;
  actions?: React.ReactNode;
  text?: React.ReactNode;
  icon: IconNames;
  noticeType: 'success' | 'neutral' | 'info' | 'error';
  isActive: boolean;
  className?: string;
}

export function NoticeCard({ title, actions, text, icon, noticeType, isActive, className }: INoticeCardProps) {
  const NoticeCardClasses = classNames({
    [styles.NoticeCard]: true,
    [styles[noticeType]]: noticeType,
    [styles.active]: isActive,
  });
  const IconContainerClasses = classNames({
    [styles.iconContainer]: true,
    [styles[noticeType]]: noticeType,
  });

  return (
    <div className={classNames(NoticeCardClasses, className)}>
      {icon && (
        <div className={IconContainerClasses}>
          <Icon name={icon} appearance="light" size="medium" />
        </div>
      )}
      {title && <div className={styles.title}>{title}</div>}
      {actions && <div className={styles.actions}>{actions}</div>}
      {text && <div className={styles.text}>{text}</div>}
    </div>
  );
}
