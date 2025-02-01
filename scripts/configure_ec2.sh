#!/usr/bin/env bash

DEBIAN_FRONTEND=noninteractive

# Update package index and upgrade system packages
sudo apt update
sudo apt upgrade -y

# Install OpenJDK 21
echo "Installing OpenJDK 21..."
sudo apt install openjdk-21-jdk -y
java -version

# Install Maven
echo "Installing Maven..."
sudo apt install maven -y

# Install unzip
echo "Installing unzip..."
sudo apt install unzip -y

# Setting up Swap (for low memory systems)
#echo "Setting up 1GB Swap..."
#sudo fallocate -l 1G /swapfile
#sudo chmod 600 /swapfile
#sudo mkswap /swapfile
#sudo swapon /swapfile
#echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
#free -h
#
## Install MySQL server
#echo "Installing MySQL Server..."
#sudo apt install mysql-server -y
#
## Enable MySQL service
#echo "Enabling MySQL Service..."
#sudo systemctl enable mysql
#
## Check MySQL service status
#echo "Checking MySQL Service Status..."
#sudo systemctl status mysql
#
## Start MySQL service
#echo "Starting MySQL Service..."
#sudo systemctl start mysql
#
## Verify if MySQL service started successfully
#MYSQL_STATUS=$(sudo systemctl is-active mysql)
#retries=5
#until [ "$MYSQL_STATUS" = "active" ] || [ "$retries" -eq 0 ]; do
#    echo "Waiting for MySQL to start..."
#    sleep 5
#    retries=$((retries-1))
#    MYSQL_STATUS=$(sudo systemctl is-active mysql)
#done
#
#if [ "$MYSQL_STATUS" != "active" ]; then
#    echo "MySQL Service failed to start. Checking logs..."
#    sudo tail -n 20 /var/log/mysql/error.log
#    exit 1
#else
#    echo "MySQL Service started successfully."
#fi

sudo useradd -M -s /usr/sbin/nologin csye6225

# Ensure git is not installed
if command -v git &> /dev/null; then
    echo "Git is installed, removing it..."
    sudo apt remove --purge -y git
    sudo apt autoremove -y
else
    echo "Git is not installed."
fi