package com.radix.utils.helm.http4s.vault

import com.radix.utils.helm.vault._

trait VaultInterface[F[_]] {

  def sysInitStatus: F[Either[VaultError, Boolean]]

  def sysInit(req: InitRequest): F[Either[VaultError, InitResponse]]

  // https://www.vaultproject.io/api/system/seal-status.html
  def sealStatus(): F[Either[VaultError, SealStatusResponse]]

  // https://www.vaultproject.io/api/system/unseal.html
  def unseal(req: UnsealRequest): F[Either[VaultError, UnsealResponse]]

  // https://www.vaultproject.io/api/system/plugins-catalog.html#register-plugin
  def registerPlugin(pluginType: PluginType, name: String, req: RegisterPluginRequest): F[Either[VaultError, Unit]]

  // https://www.vaultproject.io/api/system/mounts.html#enable-secrets-engine
  def enableSecretsEngine(pluginPath: String, req: EnableSecretsEngine): F[Either[VaultError, Unit]]

  // https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#configauth_code_url
  def authCodeUrl(pluginPath: String, req: AuthCodeUrlRequest): F[Either[VaultError, AuthCodeUrlResponse]]

  // https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#put-write-2
  def updateCredential(pluginPath: String,
                       credentialName: String,
                       req: UpdateCredentialRequest): F[Either[VaultError, Unit]]
  // https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#get-read-1
  def getCredential(pluginPath: String, credentialName: String): F[Either[VaultError, CredentialResponse]]

  // https://www.vaultproject.io/api/secret/kv/kv-v2.html#create-update-secret
  def createSecret(name: String, req: CreateSecretRequest): F[Either[VaultError, Unit]]

  // https://www.vaultproject.io/api/secret/kv/kv-v2.html#read-secret-version
  def getSecret(name: String): F[Either[VaultError, KVGetResult]]

  def createOauthSecret(name: String, req: CreateSecretRequest): F[Either[VaultError, Unit]]

  def getOauthSecret(name: String): F[Either[VaultError, KVOauthGetResult]]
}