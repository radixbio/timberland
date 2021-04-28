#!/usr/bin/env bash
set -xu

copy_logs() {
#  set +e
  mkdir /home/ubuntu/nomad-logs
  rsync -q -av --relative /opt/radix/nomad/alloc/./* /home/ubuntu/nomad-logs
  chmod -R 777 /home/ubuntu/nomad-logs
  journalctl -u consul > /tmp/consul.log
  journalctl -u nomad > /tmp/nomad.log
  journalctl -u vault > /tmp/vault.log
  journalctl -u consul-template > /tmp/consul-template.log
#  set -e
}

trap copy_logs EXIT

docker swarm init

until apt install -y jq; do
  echo "Waiting for apt install"
  sleep 2
done

until apt install -y socat; do
  echo "Waiting for apt install"
  sleep 2
done

until dpkg -i radix-timberland_0.1_all.deb; do
  echo "waiting for dpkg..."
  sleep 2
done

cd /opt/radix/timberland/exec
./timberland enable all
./timberland disable elemental
./timberland disable runtime
./timberland disable algs
./timberland disable utils
./timberland disable device_drivers
./timberland disable tui
./timberland disable retool
./timberland disable elasticsearch
./timberland disable nginx
./timberland start
./timberland enable nginx

TIMBERLAND_EXIT_CODE=$?
echo "Timberland exit code: $TIMBERLAND_EXIT_CODE"

sleep 3

chmod +x /home/ubuntu/gather-results.sh
/home/ubuntu/gather-results.sh | tee /tmp/results.log
/home/ubuntu/service_test.py | tee /tmp/service_test.log

TEST_EXIT_CODE=${PIPESTATUS[0]}
echo "Service test exit code: $TEST_EXIT_CODE"

if [ "$TIMBERLAND_EXIT_CODE" -eq 0 ] && [ "$TEST_EXIT_CODE" -eq 0 ]; then
  EXIT_CODE=0
else
  EXIT_CODE=1
fi

exit $EXIT_CODE
