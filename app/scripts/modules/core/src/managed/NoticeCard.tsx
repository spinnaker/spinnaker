import React from 'react';
import classNames from 'classnames';
import styles from './NoticeCard.module.css';

interface INoticeCardProps {
  title: JSX.Element | string;
  text: JSX.Element | string;
  icon: string;
  noticeType: 'success' | 'neutral' | 'info' | 'error';
  isActive: boolean;
  className?: string;
}

export function NoticeCard({ title, text, icon, noticeType, isActive, className }: INoticeCardProps) {
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
          <i className={classNames(styles.icon, 'ico', `icon-${icon}`)} />
        </div>
      )}
      {title && <div className={styles.title}>{title}</div>}
      {text && <div className={styles.text}>{text}</div>}
    </div>
  );
}
