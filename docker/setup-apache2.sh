apt-get update
apt-get install apache2 -y
service apache2 stop
a2enmod proxy proxy_ajp proxy_http rewrite deflate headers proxy_balancer proxy_connect proxy_html xml2enc
