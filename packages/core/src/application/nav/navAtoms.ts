import { atom } from 'recoil';
import { CollapsibleSectionStateCache } from '../../cache';

export const verticalNavExpandedAtom = atom({
  key: 'verticalNavExpanded',
  default: !CollapsibleSectionStateCache.isSet('verticalNav') || CollapsibleSectionStateCache.isExpanded('verticalNav'),
  effects: [
    ({ setSelf, trigger }) => {
      if (trigger === 'get') {
        // Avoid expensive initialization
        setSelf(
          !CollapsibleSectionStateCache.isSet('verticalNav') || CollapsibleSectionStateCache.isExpanded('verticalNav'),
        );
      }
      CollapsibleSectionStateCache.onChange('verticalNav', (expanded: boolean) => {
        setSelf(expanded);
      });

      return () => {
        // clean up
        CollapsibleSectionStateCache.onChange('verticalNav', null);
      };
    },
  ],
});
