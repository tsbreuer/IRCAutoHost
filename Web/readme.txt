Server setup instructions

install nginx:
sudo apt-get install nginx

Follow instructions at https://letsencrypt.org/ to get SSL Cert on your linux platform

Setup the NGINX config:
move autohost.moe nginx config to /etc/nginx/sites-available/
cd /etc/nginx/sites-available/
sudo rm default
sudo ln -s ../sites-available/autohost.moe
sudo nginx -s reload

update the web-content root
load the new Web.jar
