packer {
  required_plugins {
    amazon = {
      version = ">= 1.0.0, <2.0.0"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "ami_name" {
  type    = string
  default = "webapp-ami"
}

variable "vpc_id" {
  type    = string
  default = "vpc-0e8a1103a6cc2c656"
}

variable "subnet_id" {
  type    = string
  default = "subnet-04d5f910dbee0d815"
}

variable "app_war" {
  type        = string
  default     = "target/webapp-0.0.1-SNAPSHOT.war"
  description = "Path to the Spring Boot WAR file"
}

variable "source_ami" {
  type    = string
  default = "ami-0cad6ee50670e3d0e"
}

variable "ssh_username" {
  type    = string
  default = "ubuntu"
}

variable "DB_USERNAME" {
  type        = string
  default     = ""
  description = "Database username"
}

variable "DB_PASSWORD" {
  type        = string
  default     = ""
  description = "Database password"
}

variable "DB_URL" {
  type        = string
  default     = ""
  description = "Database connection URL"
}

variable "SERVER_PORT" {
  type        = string
  default     = ""
  description = "Server port"
}

variable "aws_demo_accountid" {
  type        = string
  default     = ""
  description = "Demo AWS Account ID"
}

source "amazon-ebs" "webapp" {
  region        = var.aws_region
  instance_type = "t2.small"
  ami_name      = "${var.ami_name}-${regex_replace(timestamp(), "[^a-zA-Z0-9-]", "")}"

  vpc_id                      = var.vpc_id
  subnet_id                   = var.subnet_id
  associate_public_ip_address = true
  source_ami                  = var.source_ami
  ssh_username                = var.ssh_username
  ami_users                   = [var.aws_demo_accountid]
  tags = {
    Name = "Webapp-AMI"
  }
}

build {
  name    = "Build Java Web App AMI"
  sources = ["source.amazon-ebs.webapp"]

  # Step 1: Install dependencies
  provisioner "shell" {
    script = "scripts/configure_ec2.sh"
  }

  # Step 2: Create non-login user
  provisioner "shell" {
    inline = [
      "if id -u csye6225 >/dev/null 2>&1; then echo 'User csye6225 already exists'; else sudo useradd -M -s /usr/sbin/nologin csye6225; fi",
      "sudo mkdir -p /opt/webapp",
      "sudo mkdir -p /opt/webapp/logs",
      "sudo chown -R ubuntu:ubuntu /opt/webapp",
      "sudo chmod -R 744 /opt/webapp"
    ]
  }


  # Step 3: Copy the application WAR file
  provisioner "file" {
    source      = var.app_war
    destination = "/opt/webapp/webapp.war"
  }

  #  provisioner "shell" {
  #    inline = [
  #      "sudo chown csye6225:csye6225 /opt/webapp/webapp.war"
  #    ]
  #  }

  # Step 4: Install the CloudWatch agent
  provisioner "shell" {
    inline = [
      "sudo apt-get -y update",
      "sudo apt-get install -y curl",
      "curl -O https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb",
      "sudo dpkg -i -E ./amazon-cloudwatch-agent.deb"
    ]
  }

  # Step 5: Create directories for CloudWatch agent logs
  provisioner "shell" {
    inline = [
      "sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc",
      "sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/logs",
      "sudo chown -R ubuntu:ubuntu /opt/aws/amazon-cloudwatch-agent",
      "sudo chmod -R 755 /opt/aws/amazon-cloudwatch-agent"
    ]
  }

  # Step 6: Copy the CloudWatch configuration file
  provisioner "file" {
    source      = "packer/cloudwatch-config.json"
    destination = "/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json"
  }

  # Step 7: Configure the CloudWatch agent to use the configuration file
  #  provisioner "shell" {
  #    inline = [
  #      "sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s",
  #      "sudo systemctl daemon-reload",
  #      "sudo systemctl enable amazon-cloudwatch-agent",
  #      "sudo systemctl start amazon-cloudwatch-agent"
  #    ]
  #  }

  provisioner "shell" {
    inline = [
      "sudo chmod 744 /opt/webapp/webapp.war",
      "sudo chown csye6225:csye6225 /opt/webapp/webapp.war",
      "sudo chmod -R 744 /opt/webapp/logs",
      "sudo chown csye6225:csye6225 /opt/webapp/logs"
    ]
  }

  #  provisioner "shell" {
  #    inline = [
  #      "echo 'DB_USERNAME=${var.DB_USERNAME}' | sudo tee /opt/webapp/.env",
  #      "echo 'DB_PASSWORD=${var.DB_PASSWORD}' | sudo tee -a /opt/webapp/.env",
  #      "echo 'DB_URL=${var.DB_URL}' | sudo tee -a /opt/webapp/.env",
  #      "echo 'SERVER_PORT=${var.SERVER_PORT}' | sudo tee -a /opt/webapp/.env",
  #      "sudo chmod 600 /opt/webapp/.env",
  #      "sudo chown csye6225:csye6225 /opt/webapp/.env"
  #    ]
  #  }

  provisioner "shell" {
    inline = [
      "sudo touch /opt/webapp/.env",
      "sudo chmod 600 /opt/webapp/.env",
      "sudo chown csye6225:csye6225 /opt/webapp/.env",
      "sudo chown -R csye6225:csye6225 /opt/webapp"
    ]
  }


  # Step 6: Run MySQL commands (ALTER USER, CREATE USER and DATABASE)
  #  provisioner "shell" {
  #    inline = [
  #      "sudo mysql -u ${var.DB_USERNAME} -e \"ALTER USER '${var.DB_USERNAME}'@'localhost' IDENTIFIED WITH mysql_native_password BY '${var.DB_PASSWORD}';\""
  #    ]
  #  }

  # Step 4: Configure systemd service
  provisioner "file" {
    source      = "packer/webapp.service"
    destination = "/tmp/webapp.service"
  }

  # Step 5: Configure systemd to run the Spring Boot app as a service
  provisioner "shell" {
    inline = [
      "sudo mv /tmp/webapp.service /etc/systemd/system/webapp.service",
      "sudo systemctl daemon-reload",
      "sudo systemctl enable webapp.service",
      "sudo systemctl start webapp.service"
    ]
  }

  post-processor "manifest" {
    output     = "manifest.json"
    strip_path = true
  }
}