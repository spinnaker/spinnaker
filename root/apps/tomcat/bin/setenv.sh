#
# $Id: //depot/cloud/rpms/nflx-tomcat/root/apps/tomcat/bin/sample-setenv.sh#1 $
# $DateTime: 2011/06/06 22:23:08 $
# $Change: 863252 $
#
test -f /etc/profile.d/netflix_environment.sh && source /etc/profile.d/netflix_environment.sh
NETFLIX_ENVIRONMENT=${NETFLIX_ENVIRONMENT:-"test"}
GCLOG=/apps/tomcat/logs/gc.log
CATALINA_PID=/apps/tomcat/logs/catalina.pid
JAVA_HOME=${JAVA_HOME:-/apps/java}
JRE_HOME=${JAVA_HOME}

let GB=`free -m | grep '^Mem:' | awk '{print $2}'`\*90/102400
HEAP="$GB"g
NEWGEN_RATIO="4"
EXTRA_OPTS="-XX:MaxTenuringThreshold=4 -XX:SurvivorRatio=10 -XX:TargetSurvivorRatio=90 -XX:CMSInitiatingOccupancyFraction=75 -XX:+UseCMSInitiatingOccupancyOnly"

if [ $GB -ge 50 ] ; then
  NEWGEN_RATIO="2"
  EXTRA_OPTS="-XX:MaxTenuringThreshold=25"
fi


if [ "$1" == "start" ]; then
 JAVA_OPTS="$EXTRA_OPTS \
    -verbose:sizes \
    -Xloggc:$GCLOG \
    -Xmx$HEAP -Xms$HEAP \
    -XX:NewRatio=$NEWGEN_RATIO \
    -XX:MaxPermSize=512m \
    -Xss1024k \
    -XX:+UseParNewGC \
    -XX:+UseConcMarkSweepGC \
    -XX:+CMSConcurrentMTEnabled \
    -XX:+CMSScavengeBeforeRemark \
    -XX:+PrintGCDateStamps \
    -XX:+PrintGCDetails \
    -XX:+ExplicitGCInvokesConcurrent \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/apps/tomcat/logs/ \
    -XX:-UseGCOverheadLimit \
    -Dfile.encoding=UTF8 \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.port=7500 \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Dnetflix.environment=$NETFLIX_ENVIRONMENT"
#####
#
#
# Separate options for stopping Tomcat. It is important to not use the same start options, at least for
# gclog file name. Using the same gclog file for stopping clobbers the gclogs for the running tomcat.

 test -f /apps/tomcat/bin/.logrotate.conf && logrotate -f /apps/tomcat/bin/.logrotate.conf
else
 JAVA_OPTS=""
fi

export JAVA_OPTS
