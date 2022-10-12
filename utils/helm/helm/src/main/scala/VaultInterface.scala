package com.radix.utils.helm.http4s.vault

import com.radix.utils.helm.vault._
import io.circe.{Decoder, Json}

// the following methods are radix-specific, not vault specific
trait RadixVaultInterface[F[_]] {

  def createEntity(name: String, policies: List[String], metadata: Map[String, Json]): F[Either[VaultError, String]]

  def aliasEntity(
    name: String,
    entityID: String,
    mountAccessor: String,
    metadata: Map[String, Json],
  ): F[Either[VaultError, Unit]]

  def readEntity[T: Decoder](id: String): F[Either[VaultError, T]]

  def updateEntity(
    id: String,
    name: String,
    policies: List[String],
    metadata: Map[String, Json],
    disabled: Boolean,
  ): F[Either[VaultError, Unit]]

  def listEntities: F[Either[VaultError, List[String]]]

}

trait VaultInterface[F[_]] extends RadixVaultInterface[F] {

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
  def updateOauthCredential(
    pluginPath: String,
    credentialName: String,
    req: UpdateCredentialRequest,
  ): F[Either[VaultError, Unit]]

  // https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#get-read-1
  def getOauthCredential(pluginPath: String, credentialName: String): F[Either[VaultError, CredentialResponse]]

  // https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#delete-delete-2
  def deleteOauthCredential(pluginPath: String, credentialName: String): F[Either[VaultError, Unit]]

  // https://www.vaultproject.io/api/secret/kv/kv-v2.html#create-update-secret
  def createSecret(name: String, req: CreateSecretRequest): F[Either[VaultError, Unit]]

  // https://www.vaultproject.io/api/secret/kv/kv-v2.html#read-secret-version
  def getSecret[R](name: String)(implicit d: Decoder[R]): F[Either[VaultError, KVGetResult[R]]]

  // https://www.vaultproject.io/api-docs/secret/kv/kv-v2#delete-latest-version-of-secret
  def deleteSecret(name: String): F[Either[VaultError, Unit]]

  // https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#put-write-1
  def createOauthServer(pluginPath: String, name: String, req: CreateOauthServerRequest): F[Either[VaultError, Unit]]

  // https://github.com/puppetlabs/vault-plugin-secrets-oauthapp#delete-delete-1
  def deleteOauthServer(pluginPath: String, name: String): F[Either[VaultError, Unit]]

  def createUser(username: String, password: String, policies: List[String]): F[Either[VaultError, Unit]]

  def login(username: String, password: String): F[Either[VaultError, LoginResponse]]

  def enableAuthMethod(authMethod: String): F[Either[VaultError, Unit]]

  def getCertificate(pkiName: String, commonName: String, ttl: String): F[Either[VaultError, CertificateResponse]]

}
