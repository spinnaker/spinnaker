#!/bin/bash
PACKAGEDIRS=$*

# Run this script from the 'deck/app/scripts/modules' directory
cd `dirname $0`

# Show help text if no modules were specified
if [ "$1" == "" ] ; then
  echo "Bump versions in package.json and (optionally) publish deck modules"
  echo "$0 <module1> <module2>"
  exit 1
fi

# Check for clean master
echo "Deck package publisher ---> Checking that you are on the 'master' branch and are up to date with the remote..."
./assert_clean_master.sh || exit 2

# Check that all modules (cli arguments) exist
echo "Deck package publisher ---> Checking that all packages (${PACKAGEDIRS}) exist..."
CWD=`pwd`
for DIR in ${PACKAGEDIRS} ; do
  if [ ! -e ${DIR}/package.json ] ; then
    echo "$CWD/${DIR}/package.json does not exist"
    exit 3
  fi
done

# Switch to a new bump-package<n> branch
echo "Deck package publisher ---> Creating a new branch for package bumps..."
COUNT=
while git show-ref --verify --quiet refs/heads/bump-package$COUNT ; do
  let COUNT=COUNT+1
done
git checkout -b bump-package$COUNT
TEMPORARYBRANCH=`git branch | grep \* | cut -d ' ' -f2`
echo "Deck package publisher ---> Created and switched to temporary branch '${TEMPORARYBRANCH}'..."

# Run yarn
echo "Deck package publisher ---> Updating to latest dependencies..."
pushd ../../../
yarn
popd

# Determine upstream dependencies and proper build order
echo "Deck package publisher ---> Preparing to publish ${PACKAGEDIRS}..."
BUILDORDER=`./build_order.sh ${PACKAGEDIRS}`
echo "Deck package publisher ---> Package build order:"
echo "${BUILDORDER}"
echo

BR=$'\n';
PULLREQUESTMESSAGE="";
PUBLISHBRANCH="";
# Loop over packages to build and either a) Build and Publish to NPM or b) Build only
for DIR in ${BUILDORDER} ; do
  # Check if the current package to build is in PACKAGEDIRS (if so, publish it)
  if echo ${PACKAGEDIRS} | grep -F -q -w "${DIR}" ; then
    echo "Deck package publisher ---> Preparing to publish '${DIR}'..."

    echo "Deck package publisher ---> Creating changelog for '${DIR}'..."
    COMMITS=`echo "" && echo "" && ./show_unpublished_commits.sh ${DIR}`
    pushd ${DIR} > /dev/null
    echo "${COMMITS}"

    PACKAGE=`node -e 'console.log(JSON.parse(require("fs").readFileSync("package.json")).name)'`

    echo "Deck package publisher ---> Bumping version for '${PACKAGE}'..."
    npm version patch --no-git-tag-version
    VERSION=`node -e 'console.log(JSON.parse(require("fs").readFileSync("package.json")).version)'`

    echo "Deck package publisher ---> Commiting version bump of '${PACKAGE}' to ${VERSION}..."
    COMMITMSG="chore(${DIR}): Bump version to ${VERSION}${COMMITS}";
    PULLREQUESTMESSAGE="${PULLREQUESTMESSAGE}### ${COMMITMSG}${BR}${BR}"
    git commit -m "${COMMITMSG}" package.json

    if [ "x${PUBLISHBRANCH}" == "x" ] ; then
      PUBLISHBRANCH="bump-package-${DIR}-to-${VERSION}"
    else
      PUBLISHBRANCH="${PUBLISHBRANCH}-and-${DIR}-to-${VERSION}"
    fi
    echo "Deck package publisher ---> Updated publish branch to ${PUBLISHBRANCH}"

    echo
    read -t 1 -n 10000 discard
    read -p "Ready to build and publish version ${VERSION} of '${PACKAGE}' to npm? (y/n) " -n 1 -r
    echo
    if [ "$REPLY" == "y" ] ; then
      echo "Deck package publisher ---> Publishing ${PACKAGE}..."
      npm publish
    else
      echo "Deck package publisher ---> Building (but NOT publishing) ${PACKAGE}..."
      yarn prepublishOnly
    fi
  else
    echo "Deck package publisher ---> Building (but not publishing) upstream dependency '${DIR}'..."
    pushd ${DIR} > /dev/null
    yarn prepublishOnly
  fi

  popd > /dev/null
done

# Create a branch with the package bump versions in it
# Github will use this as the PR title
echo "Deck package publisher ---> Creating publish branch '${PUBLISHBRANCH}'..."
git checkout -b ${PUBLISHBRANCH} || exit 4
echo "Deck package publisher ---> Deleting temporary branch '${TEMPORARYBRANCH}'..."
git branch -D ${TEMPORARYBRANCH} || exit 5

echo
read -t 1 -n 10000 discard
read -p "Ready to push branch ${PUBLISHBRANCH} to 'origin'? (y/n) " -n 1 -r
echo
if [ "$REPLY" == "y" ] ; then
  echo "Deck package publisher ---> Pushing ${PUBLISHBRANCH} to 'origin'..."
  # https://dwmkerr.com/a-portable-and-magic-free-way-to-open-pull-requests-from-the-command-line/
  # Push to origin, grabbing the output but then echoing it back.
  PUSHOUTPUT=`git push origin -u ${PUBLISHBRANCH} 2>&1`
  echo "${PUSHOUTPUT}"

  # If there's anything which starts with http, it's a good guess it'll be a
  # link to GitHub/GitLab/Whatever. So open it.
  LINK=$(echo "${PUSHOUTPUT}" | grep -o 'http.*' | sed -e 's/[[:space:]]*$//')
  if [ "x${LINK}" != "x" ] && which python > /dev/null ; then
    echo "Deck package publisher ---> Creating pull request at: ${LINK}..."
    python -mwebbrowser "${LINK}"
    if which pbcopy > /dev/null ; then
      echo "${PULLREQUESTMESSAGE}" | pbcopy
      echo "";
      echo "";
      echo "*****************************************************";
      echo "* Pull request message copied to your clipboard!    *";
      echo "* Paste it in and use the 'Squash and merge' button *";
      echo "*****************************************************";
      echo "";
      echo "";
    fi
  fi
fi

echo "Deck package publisher ---> Switching back to 'master'..."
git checkout master

echo "Deck package publisher ---> Deleting pull request branch ${PUBLISHBRANCH}..."
git branch -D ${PUBLISHBRANCH}
