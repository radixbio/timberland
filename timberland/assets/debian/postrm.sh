if [ "$1" = "purge" ]; then
  find /opt/radix/nomad | grep secrets$ | xargs umount 2> /dev/null
  rm -rf /opt/radix/*
fi