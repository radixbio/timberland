#!/usr/bin/env bash

docker swarm init

until dpkg -i radix-timberland_0.1_amd64.deb
do
  echo "waiting for dpkg..."
  sleep 2
done

cd /opt/radix/timberland/exec
docker network create --attachable -d weaveworks/net-plugin:2.6.0 weave  --ip-range 10.32.0.0/12 --subnet 10.32.0.0/12
echo -n "Vu6nzjx8T_sy14pxrepu" | docker login registry.gitlab.com -u radix-timberland-ci --password-stdin
./timberland runtime start --dev

sleep 3

/home/ubuntu/service_test.py > /home/ubuntu/service_test.log

EXIT_CODE=$?

echo "Service test exit code: $EXIT_CODE"

mkdir /home/ubuntu/nomad-logs
cp /opt/radix/nomad/alloc/*/alloc/logs/*.0 /home/ubuntu/nomad-logs/

exit $EXIT_CODE
