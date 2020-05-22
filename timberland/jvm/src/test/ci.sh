#!/usr/bin/env bash

# This script is responsible for starting an EC2 instance, installing timberland, and running it.
# Note: The following environment variables must be provided:
# * CI_EC2_AMI
# * CI_EC2_INSTANCE_SIZE
# * CI_EC2_SECURITY_GROUP
# * CI_EC2_OS_FLAVOR (must be: {centos, ubuntu})

set -ex

# Is there a more concise way to represent this?
if [ "$1" == "persist" ];
then
  PERSIST=true
else
  PERSIST=false
fi

if [ "$CI_EC2_OS_FLAVOR" == "centos" ];
then
  PACKAGE_LOCATION="./timberland/jvm/timberland-rpm-amd64.rpm"
  INSTALLATION_SCRIPT="./timberland/jvm/src/test/install-rpm.sh"
  AWS_INSTANCE_SSH_USER=centos
else
  PACKAGE_LOCATION="./timberland/jvm/radix-timberland_0.1_amd64.deb"
  INSTALLATION_SCRIPT="./timberland/jvm/src/test/install-deb.sh"
  AWS_INSTANCE_SSH_USER=ubuntu
fi

if ! [ -x "$(command -v aws)" ]; then
  echo "Please install aws-cli: https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-welcome.html"
  exit 1
fi

if ! [ -x "$(command -v jq)" ]; then
  echo "Please install jq"
  exit 1
fi

if ! [ -x "$(command -v ssh)" ]; then
  echo "Please install ssh"
  exit 1
fi

AWS_RESULT=$(aws ec2 run-instances --image-id $CI_EC2_AMI --instance-type $CI_EC2_INSTANCE_SIZE --security-group-ids $CI_EC2_SECURITY_GROUP --associate-public-ip-address --key-name radix-ci --instance-initiated-shutdown-behavior terminate)
AWS_INSTANCE_ID=$(echo $AWS_RESULT | jq -r .Instances[0].InstanceId)

echo "Waiting for EC2 instance to become ready..."

aws ec2 wait instance-running --instance-ids $AWS_INSTANCE_ID

AWS_INSTANCE_IP=$(aws ec2 describe-instances --instance-ids $AWS_INSTANCE_ID | jq -r .Reservations[0].Instances[0].PublicIpAddress)

function test_ssh {
  nc -w 2 -z $AWS_INSTANCE_IP 22
  echo $?
}

while [ $(test_ssh) -ne 0 ]
do
  echo "Waiting for SSH..."
  sleep 2
done

if [ "$1" == "persist" ];
then
  # Remove the crontab entry for the automatic power-off script
  ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP 'crontab -r' || exit 0
fi

echo "Transferring package to instance ($AWS_INSTANCE_IP)..."

scp -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $PACKAGE_LOCATION $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~

scp -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" ./timberland/jvm/src/test/service_test.py $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~

ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP 'sudo su && bash -s' < $INSTALLATION_SCRIPT

echo "@@@@@@@@ SSH ended"

scp -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~/service_test.log /tmp/service_test.log

echo "@@@@@@@@ Got service test log"

scp -r -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~/nomad-logs/* /tmp/nomad-logs

echo "@@@@@@@@ Got nomad logs"

if [ "$PERSIST" != "true" ];
then
  # This should terminate the instance due to "--instance-initiated-shutdown-behavior terminate"
  ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP 'sudo poweroff' || exit 0
else
  echo "CONNECTION INFO: ssh $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP"
fi
