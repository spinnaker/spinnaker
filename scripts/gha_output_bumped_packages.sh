#!/usr/bin/env bash
# If the last commit was a package bump,
if git log -1 --pretty=%B | head -n 1 | grep "chore(publish): publish packages" ; then
  BUMPS=$(git log -1 --pretty=%B | grep "^ - " | sed -e 's/^ - //' -e 's/^@spinnaker\///');

  ## Github Action Step output variable: bumps
  echo ::set-output name=bumps::${BUMPS}
fi
