import classNames from 'classnames';
import React from 'react';
import './CollapsibleElement.less';

export const CollapsibleElement: React.FC<{ maxHeight: number }> = ({ children, maxHeight }) => {
  const [isCollapsed, setIsCollapsed] = React.useState(true);
  const [isOverflowing, setIsOverflowing] = React.useState(false);
  const contentRef = React.useRef<HTMLDivElement>(null);

  const checkIsOverflowing = React.useCallback(() => {
    if (!contentRef.current) return;
    setIsOverflowing(contentRef.current.offsetHeight < contentRef.current.scrollHeight);
  }, []);

  React.useEffect(() => {
    checkIsOverflowing();
  }, [children, checkIsOverflowing]);

  React.useEffect(() => {
    window.addEventListener('resize', checkIsOverflowing);
    return () => window.removeEventListener('resize', checkIsOverflowing);
  }, []);

  return (
    <div className="collapsible-element">
      <div
        style={{ maxHeight: isCollapsed ? maxHeight : undefined }}
        className={classNames(['content', { ['collapsed']: isCollapsed && isOverflowing }])}
        ref={contentRef}
      >
        {children}
      </div>
      {(isOverflowing || !isCollapsed) && (
        <button className="expand-button" onClick={() => setIsCollapsed((state) => !state)}>
          {isCollapsed ? 'Read more' : 'Read less'}
        </button>
      )}
    </div>
  );
};
