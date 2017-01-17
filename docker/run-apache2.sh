# Set any missing env variables used to configure deck

_DECK_HOST=$DECK_HOST
_DECK_PORT=$DECK_PORT
_API_HOST=$API_HOST

if [ -z "$_DECK_HOST" ];
then
    _DECK_HOST=0.0.0.0
fi

if [ -z "$_DECK_PORT" ];
then
    _DECK_PORT=9000
fi

if [ -z "$_API_HOST" ];
then
    _API_HOST=http://localhost:8084
fi

# Generate spinnaker.conf site & enable it

cp docker/spinnaker.conf.gen spinnaker.conf

sed -ie 's|{%DECK_HOST%}|'$_DECK_HOST'|g' spinnaker.conf
sed -ie 's|{%DECK_PORT%}|'$_DECK_PORT'|g' spinnaker.conf
sed -ie 's|{%API_HOST%}|'$_API_HOST'|g' spinnaker.conf

mkdir -p /etc/apache2/sites-available
mv spinnaker.conf  /etc/apache2/sites-available

a2ensite spinnaker

# Update ports.conf to reflect desired deck host

cp docker/ports.conf.gen ports.conf

sed -ie "s/{%DECK_HOST%}/$_DECK_HOST/g" ports.conf
sed -ie "s/{%DECK_PORT%}/$_DECK_PORT/g" ports.conf

mv ports.conf /etc/apache2/ports.conf

apache2ctl -D FOREGROUND 
