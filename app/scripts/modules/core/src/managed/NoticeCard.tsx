import React from 'react';
import classNames from 'classnames';
import styles from './NoticeCard.module.css';

interface INoticeCardProps {
  title: string;
  text: string;
  icon: string;
  noticeType: 'ok'; // TypeScript doesn't like this being arbitrary strings because it can't validate styles[noticeType]
  isActive: boolean;
}

export function NoticeCard({ title, text, icon, noticeType, isActive }: INoticeCardProps) {
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
    <div className={NoticeCardClasses}>
      {icon && (
        <div className={IconContainerClasses}>
          <i className={`ico icon-${icon}`} />
        </div>
      )}
      {title && <div className={styles.title}>{title}</div>}
      {text && <div className={styles.text}>{text}</div>}
    </div>
  );
}
