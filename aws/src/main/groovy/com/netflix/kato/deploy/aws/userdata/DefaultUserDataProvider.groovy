package com.netflix.kato.deploy.aws.userdata

import com.netflix.frigga.Names
import org.springframework.stereotype.Component

@Component
class DefaultUserDataProvider implements UserDataProvider {
  @Override
  String getUserData(String asgName, String launchConfigName, String region) {
    Names names = Names.parseName(asgName)

    perforce names, launchConfigName, region
  }

  private static def perforce(Names names, String launchConfigName, region) {
    """\
#!/bin/bash
# preV2
# udf0 begin
exec 1>/var/log/userdata.log 2>&1
ln -sv /var/{log,ec2}/userdata.log
set -x

if [ -e '/etc/lsb-release' ]; then
    . /etc/lsb-release
    export BASEOS=\$(echo \$DISTRIB_ID | tr '[A-Z]' '[a-z]')
elif [ -e '/etc/redhat-release' ]; then
    if grep -q -i centos /etc/redhat-release; then
        export BASEOS=centos
    else
        export BASEOS=rhel
    fi
fi

PATH=/bin:/usr/bin:/usr/sbin:/sbin

app="$names.app"
appenv="test"
region="$region"
appuser="\${app}\${appenv}"
stack="$names.stack"
cluster="$names.cluster"
autogrp="$names.group"
launchconfig="$launchConfigName"
clusterorapp=\${cluster:-\$app}
appdynappname="\$region \$appenv"
setenv_sh="/apps/tomcat/bin/setenv.sh"
NFenv="/apps/tomcat/bin/netflix.env"
server_template_xml="/apps/tomcat/conf/server_template.xml"
server_xml="/apps/tomcat/conf/server.xml"
instanceid=`/usr/bin/instance-id`

> /etc/profile.d/netflix_environment.sh
if [ "\$BASEOS" = "ubuntu" ]; then
    echo "TODO: rotate nflx apt mirrors"
else
    test -x /usr/bin/regionalize_cfg && /usr/bin/regionalize_cfg /etc/yum.repos.d/nflx.mirrors
fi
# udf0 end
# udf-test begin
cat <<EEE >/etc/appdynamics-setup.cfg
appdynappname3="\${appdynappname}"
appdyncontroller3="test73.saas.appdynamics.com"
NETFLIX_APP=\$app
TIER=\${cluster}
EEE
aa=/apps/appagent
newbase && test -e \$aa && ! test -L \$aa && rm -rf \$aa
/usr/bin/ad-setver 3
/usr/bin/ad-cfg
# udf-test end
# udf1 begin
hostnamestring=\${clusterorapp//_/-}-\${instanceid}
hostname \$hostnamestring
if [ "\$BASEOS" = "ubuntu" ]; then
    echo \$hostnamestring > /etc/hostname
else
    printf "/^HOSTNAME/ d\\n \$\\na\\nHOSTNAME=\${hostnamestring}\\n.\\nw\\nq\\n" | ed -s /etc/sysconfig/network
fi
echo "127.0.0.1 localhost" > /etc/hosts
printf "%s\t%s %s.netflix.com %s\\n" `local-ipv4` `hostname` `hostname` `public-hostname` >> /etc/hosts
test -n "\$reg_hosts" && echo \$reg_hosts >> /etc/hosts

eval `awk -F: '/60004/ {print "amirole=" \$1}' /etc/passwd`
test -n "\$amirole" && userdel -f \$amirole
groupadd -f -g 60243 nac
useradd -m -u 60004 -g nac -c "\$app \$appenv role" \$appuser
chown -R  \${appuser}:nac /home/\${appuser}/.ssh
chmod 700 /home/\${appuser}/.ssh
chmod 600 /home/\${appuser}/.ssh/authorized_keys

test -f \${NFenv}.\${appenv} && cp -f -v \${NFenv}.\${appenv} \$NFenv

if [ -f \$setenv_sh ]
then
    printf '
/-Dnetflix.environment=/ s/-Dnetflix.environment=[^ "][^ "]*/-Dnetflix.environment=%s/
w
q
' \$appenv | ed \$setenv_sh
    printf 'CATALINA_PID=\${CATALINA_PID:-"/apps/tomcat/logs/catalina.pid"}\\n' >>  \$setenv_sh
fi
server_xml(){
test -f \$server_template_xml && \
sed -e "s/%TRUSTSTOREPW%/\$tpw/" \
-e "s/%KEYSTOREPW%/\$kpw/" \$server_template_xml > \$server_xml
}

cat <<EEE > /etc/profile.d/netflix_environment.sh
export NETFLIX_ENVIRONMENT=\$appenv
export NETFLIX_APP=\$app
export NETFLIX_STACK=\$stack
export NETFLIX_CLUSTER=\$cluster
export NETFLIX_AUTO_SCALE_GROUP=\$autogrp
export NETFLIX_LAUNCH_CONFIG=\$launchconfig
export NETFLIX_APPUSER=\$appuser
export EC2_REGION=\$region
export NFLX_CUSTOM=%%customvars%%
export BASEOS=\$BASEOS
EEE

metadatavars http://169.254.169.254/latest/meta-data/ >> /etc/profile.d/netflix_environment.sh
. /etc/profile.d/netflix_environment.sh

jmxf=/apps/jmxremote.password
jmxl=/apps/appagent/jmxremote.passwd
echo 'monitorRole netflixjmx' > \$jmxf && chmod 400 \$jmxf
rm -f \$jmxl
ln -s \$jmxf \$jmxl

/usr/local/bin/installcrontab \$appuser


#hacks
chmod 1777 /tmp
grep skipNFSInHostResources /etc/snmp/snmpd.conf || echo "skipNFSInHostResources true" >> /etc/snmp/snmpd.conf
[ -n "\${EC2_VPC_ID}" ] && service snmpd restart && ifconfig eth0 mtu 1396 # ENGTOP-144
# udf1 end
# udf2 begin
chown -R \${appuser}:nac /apps
chmod a+x /etc/nflx-init.d/*
chmod 1777 /var/tmp
touch /tmp/udf-complete
# udf2 end
    """
  }
}
