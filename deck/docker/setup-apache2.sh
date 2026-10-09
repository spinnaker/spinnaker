set -e

apt-get update
# TODO: Upgrade Apache to >=2.4.69 and curl to >=8.22 when Debian stable
# publishes fixed packages; CVE-2026-95210 still needs a GnuTLS fix.
apt-get upgrade -y
apt-get install apache2 brotli gzip -y
rm -rf /var/lib/apt/lists/*

service apache2 stop
a2enmod proxy proxy_ajp proxy_http rewrite deflate headers mime proxy_balancer proxy_connect proxy_html xml2enc
chown -R www-data:www-data /etc/apache2

mkdir -p /var/lib/apache2
chown -R www-data:www-data /var/lib/apache2

mkdir -p /var/run/apache2
chown -R www-data:www-data /var/run/apache2

mkdir -p /var/log/apache2
chown -R www-data:www-data /var/log/apache2
