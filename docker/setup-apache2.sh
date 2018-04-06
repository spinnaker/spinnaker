apt-get update
apt-get install apache2 -y
service apache2 stop
a2enmod proxy proxy_ajp proxy_http rewrite deflate headers proxy_balancer proxy_connect proxy_html xml2enc
chown -R www-data:www-data /etc/apache2

mkdir -p /var/lib/apache2
chown -R www-data:www-data /var/lib/apache2

mkdir -p /var/run/apache2
chown -R www-data:www-data /var/run/apache2
