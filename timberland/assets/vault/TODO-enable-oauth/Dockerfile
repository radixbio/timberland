FROM vault:1.3.2

RUN apk add git go make build-base
WORKDIR /opt
COPY vault_config.conf .
COPY set_up_plugin .
WORKDIR /usr/src
RUN git clone https://github.com/puppetlabs/vault-plugin-secrets-oauthapp.git oauth
WORKDIR /usr/src/oauth
COPY google.patch .
RUN patch -p1 < google.patch
RUN make
RUN mkdir /opt/plugins && \
    cp ./bin/vault-plugin-secrets-oauthapp /opt/plugins/

