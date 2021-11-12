#!/usr/bin/env bash
start_time="$(date -u +%s)"
t () {
  set +xu
  now="$(date -u +%s)"
  elapsed="$((now-start_time))"
  echo -n "T+$elapsed: "
  set -xu
}
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

# Kill automatic security updates
killall apt dpkg
dpkg --configure -a

until apt install -y jq; do
  t; echo "Waiting for apt install"
  sleep 2
done

until dpkg -i radix-timberland_0.1_all.deb; do
  t; echo "waiting for dpkg..."
  sleep 2
done

cd /opt/radix/timberland/exec || exit 1
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
# ./timberland enable nginx
# this is necessary because terraform fills in the nginx template with the currently services

TIMBERLAND_EXIT_CODE=$?
t; echo "Timberland exit code: $TIMBERLAND_EXIT_CODE"

sleep 3

chmod +x /home/ubuntu/gather-results.sh /home/ubuntu/service_test.py
t
/home/ubuntu/gather-results.sh | tee /tmp/results.log
t
/home/ubuntu/service_test.py | tee /tmp/service_test.log

TEST_EXIT_CODE=${PIPESTATUS[0]}
t; echo "Service test exit code: $TEST_EXIT_CODE"

if [ "$TIMBERLAND_EXIT_CODE" -eq 0 ] && [ "$TEST_EXIT_CODE" -eq 0 ]; then
  EXIT_CODE=0
else
  EXIT_CODE=1
fi

exit $EXIT_CODE
