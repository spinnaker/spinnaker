#!/bin/bash
#
# Copyright 2015 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# The Spinnaker debian packages have dependencies on openjdk-8-jre.
# If you have a different JDK 1.8 installed then we need to subvert
# the requirement checks.
#
# This script creates a fake debian package that claims to offer openjdk-8-jre.
# Installing it will satisfy other dependency checks, though obviously this does
# not actually provide openjdk-8-jre.

cd /tmp
cat > fake-openjdk-8-jre.txt <<EOF
Section: misc
Priority: optional
Standards-Version: 3.9.2

Package: fake-openjdk-8-jre
Version: 1.0
Provides: openjdk-8-jre
Description: Fake openjdk-8-jre dependency
EOF

if ! which equivs-build; then
    print 'installing equivs...'
    sudo apt-get install -y equivs
fi
equivs-build fake-openjdk-8-jre.txt
echo "For the record, "Java -version" says\n$(java -version)"
echo "Installing 'fake-openjdk-8-jre' package to suppress openjdk-8-jre checks".
sudo dpkg -i fake-openjdk-8-jre_1.0_all.deb
