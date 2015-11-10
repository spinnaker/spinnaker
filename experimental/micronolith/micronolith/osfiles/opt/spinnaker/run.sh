#!/bin/sh

service apache2 restart
service redis-server restart

if [ ! -d "/logs" ]; then
  mkdir /logs
fi

CURPWD=`pwd`

for svc in front50 mort oort kato orca gate;
do
  cd /opt/$svc;
  bin/$svc 2>&1 | tee /logs/$svc.log >>/logs/spinnaker.log &
done

cd $CURPWD
