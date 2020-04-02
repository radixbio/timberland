the files in this directory are referred to statically by the integration test. This can be packed up using a strategy similar to our linking of native libraries, but it's not implemented yet.

start.sh is used in the build, it's the entrypoint for the image.

the weave network must be running and attachable, and timberland must be `runtime install`ed, and you may have to clear out /tmp/nomad every once in a while, but those are the caveats.

Oh and ofc nomad's running in privileged mode