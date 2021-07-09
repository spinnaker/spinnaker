import React from 'react';
import ContentLoader from 'react-content-loader';

export const LoadingAnimation = () => (
  <ContentLoader width="100%" height={30}>
    <rect x="0" y="8" rx="5" ry="5" width="60%" height="8" />
    <rect x="0" y="22" rx="5" ry="5" width="50%" height="8" />
  </ContentLoader>
);
