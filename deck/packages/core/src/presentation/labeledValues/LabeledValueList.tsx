import React from 'react';

export function LabeledValueList(props: React.HTMLAttributes<HTMLDListElement>) {
  const { children, ...rest } = props;
  return <dl {...rest}>{children}</dl>;
}
