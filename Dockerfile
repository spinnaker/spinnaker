FROM ubuntu:14.10
MAINTAINER Dan Woods <danw@netflix.com>
RUN echo "deb http://dl.bintray.com/spinnaker/ospackages ./" > /etc/apt/sources.list.d/spinnaker.list
RUN apt-get update
RUN apt-get install -y git
RUN apt-get install -y apache2
RUN apt-get install -y curl
RUN apt-get update
RUN apt-get install --force-yes -y kato
RUN apt-get install --force-yes -y oort
RUN apt-get install --force-yes -y mort
RUN apt-get install --force-yes -y pond
RUN apt-get install --force-yes -y deck
RUN apt-get install --force-yes -y front50
RUN apt-get install -y openjdk-8-jre
RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
RUN a2enmod proxy_http
RUN echo "#!/bin/bash -x" >/tmp/fix_settings.sh
RUN echo export DECK_SCRIPT_FILE=\`find /var/www/scripts -iname \"settings*.js\" -print\` >> /tmp/fix_settings.sh
RUN echo cp \$DECK_SCRIPT_FILE \$DECK_SCRIPT_FILE.bak >>/tmp/fix_settings.sh
RUN echo sed \"s/front50Url: \'\\\(.*\\\)\'/front50Url: \'\\\/front50\'/\" \$DECK_SCRIPT_FILE \> \$DECK_SCRIPT_FILE.new >>/tmp/fix_settings.sh
RUN echo mv \$DECK_SCRIPT_FILE.new \$DECK_SCRIPT_FILE >>/tmp/fix_settings.sh
RUN echo sed \"s/oortUrl: \'\\\(.*\\\)\'/oortUrl: \'\\\/oort\'/\" \$DECK_SCRIPT_FILE \> \$DECK_SCRIPT_FILE.new >>/tmp/fix_settings.sh
RUN echo mv \$DECK_SCRIPT_FILE.new \$DECK_SCRIPT_FILE >>/tmp/fix_settings.sh
RUN echo sed \"s/mortUrl: \'\\\(.*\\\)\'/mortUrl: \'\\\/mort\'/\" \$DECK_SCRIPT_FILE \> \$DECK_SCRIPT_FILE.new >>/tmp/fix_settings.sh
RUN echo mv \$DECK_SCRIPT_FILE.new \$DECK_SCRIPT_FILE >>/tmp/fix_settings.sh
RUN echo sed \"s/pondUrl: \'\\\(.*\\\)\'/pondUrl: \'\\\/pond\'/\" \$DECK_SCRIPT_FILE \> \$DECK_SCRIPT_FILE.new >>/tmp/fix_settings.sh
RUN echo mv \$DECK_SCRIPT_FILE.new \$DECK_SCRIPT_FILE >>/tmp/fix_settings.sh
RUN echo sed \"s/katoUrl: \'\\\(.*\\\)\'/katoUrl: \'\\\/kato\'/\" \$DECK_SCRIPT_FILE \> \$DECK_SCRIPT_FILE.new >>/tmp/fix_settings.sh
RUN echo mv \$DECK_SCRIPT_FILE.new \$DECK_SCRIPT_FILE >>/tmp/fix_settings.sh
RUN echo sed \"s/credentialsUrl: \'\\\(.*\\\)\'/credentialsUrl: \'\\\/kato\'/\" \$DECK_SCRIPT_FILE \> \$DECK_SCRIPT_FILE.new >>/tmp/fix_settings.sh
RUN echo mv \$DECK_SCRIPT_FILE.new \$DECK_SCRIPT_FILE >>/tmp/fix_settings.sh
RUN chmod +x /tmp/fix_settings.sh
RUN cat /tmp/fix_settings.sh
RUN /tmp/fix_settings.sh
RUN mkdir /logs
RUN echo "service apache2 start" >/opt/run.sh
RUN echo "export JAVA_OPTS=\'-Djava.security.egd=file:/dev/./urandom\'" >>/opt/run.sh
RUN echo "/opt/kato/bin/kato 2>&1 >/logs/kato.log &" >>/opt/run.sh
RUN echo "/opt/oort/bin/oort 2>&1 >/logs/oort.log &" >>/opt/run.sh
RUN echo "/opt/mort/bin/mort 2>&1 >/logs/mort.log &" >>/opt/run.sh
RUN echo "/opt/front50/bin/front50-web 2>&1 >/logs/front50.log &" >>/opt/run.sh
RUN echo "/opt/pond/bin/pond 2>&1 >/logs/pond.log &" >>/opt/run.sh
RUN chmod +x /opt/run.sh
