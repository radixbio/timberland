#!/usr/bin/env bash

set -ex

# Is there a more concise way to represent this?
if [ "$1" == "persist" ];
then
  PERSIST=true
else
  PERSIST=false
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

AWS_RESULT=$(aws ec2 run-instances --image-id ami-066e03f91f32d7a03 --instance-type t3.2xlarge --security-group-ids sg-0c06080d080b5876f --associate-public-ip-address --key-name radix-ci --instance-initiated-shutdown-behavior terminate)
AWS_INSTANCE_ID=$(echo $AWS_RESULT | jq -r .Instances[0].InstanceId)

echo "Waiting for EC2 instance to become ready..."

aws ec2 wait instance-running --instance-ids $AWS_INSTANCE_ID

AWS_INSTANCE_IP=$(aws ec2 describe-instances --instance-ids $AWS_INSTANCE_ID | jq -r .Reservations[0].Instances[0].PublicIpAddress)
AWS_INSTANCE_SSH_USER=ubuntu

function test_ssh {
  nc -w 2 -z $AWS_INSTANCE_IP 22
  echo $?
}

while [ $(test_ssh) -ne 0 ]
do
  echo "Waiting for SSH..."
  sleep 2
done

echo "Transferring deb to instance ($AWS_INSTANCE_IP)..."

scp -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" ./timberland/jvm/radix-timberland_0.1_amd64.deb $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~

scp -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" ./timberland/jvm/src/test/service_test.py $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~

ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP 'sudo su && bash -s' <<ENDSSH
docker swarm init

until dpkg -i radix-timberland_0.1_amd64.deb
do
  echo "waiting for dpkg..."
  sleep 2
done

cd /opt/radix/timberland/exec
./timberland runtime install
docker network create --attachable -d weaveworks/net-plugin:2.6.0 weave
echo -n "Vu6nzjx8T_sy14pxrepu" | docker login registry.gitlab.com -u radix-timberland-ci --password-stdin
./timberland runtime start --dev

sleep 3

/home/ubuntu/service_test.py > /home/ubuntu/service_test.log

EXIT_CODE=$?

echo "Service test exit code: $EXIT_CODE"

mkdir /home/ubuntu/nomad-logs
cp /opt/radix/nomad/alloc/*/alloc/logs/*.0 /home/ubuntu/nomad-logs/

exit $EXIT_CODE

ENDSSH

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
  # Remove the crontab entry for the sudomatic power-off script
  ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP 'crontab -r' || exit 0
  echo "CONNECTION INFO: ssh $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP"
fi
