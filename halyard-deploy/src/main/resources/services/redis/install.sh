add-apt-repository -y ppa:chris-lea/redis-server
apt-get -q -y --force-yes install redis-server={%version%} redis-tools={%version%}