#!/usr/bin/env bash

PACKAGEDIR=$(cd $(dirname $0)/../../; pwd -P);
cd $PACKAGEDIR;

[[ ! -e package.json ]] && {
  echo "Unable to install husky commit hooks..."
  echo "package.json not found at $PACKAGEDIR"
  exit
}

[[ ! -e .git ]] && [[ ! -e ../.git ]] && {
  echo "Unable to install husky commit hooks..."
  echo ".git not found at $PACKAGEDIR nor at $PACKAGEDIR/.."
  exit
}
HUSKYBIN=$PACKAGEDIR/node_modules/.bin/husky

[[ ! -e $HUSKYBIN ]] && {
  echo "Unable to install husky commit hooks..."
  echo "husky binary not found at $HUSKYBIN"
  exit
}

DIRNAME=$(basename $PACKAGEDIR);
[[ -e ../.git ]] && INSTALLDIR=".."
[[ -e .git ]] && INSTALLDIR="."

cd $INSTALLDIR
$HUSKYBIN install

cat > .husky/pre-commit <<'EOF'
#!/usr/bin/env bash
# CHECK THIS FILE INTO SOURCE CONTROL!

. "$(dirname "$0")/_/husky.sh"

EOF

echo "[[ -e $DIRNAME/node_modules/.bin/lint-staged ]] && cd $DIRNAME; ./node_modules/.bin/lint-staged -p false" >> .husky/pre-commit
chmod +x .husky/pre-commit
