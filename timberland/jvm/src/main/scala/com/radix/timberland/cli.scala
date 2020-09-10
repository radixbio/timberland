package com.radix.timberland

import com.radix.timberland.launch.daemonutil
import io.circe.{Parser => _}
import optparse_applicative._
import optparse_applicative.types.{Doc, Parser}
import scalaz.syntax.apply._

sealed trait RadixCMD

sealed trait Runtime extends RadixCMD

sealed trait Local extends Runtime

case class Start(
  loglevel: scribe.Level = scribe.Level.Debug,
  bindIP: Option[String] = None,
  consulSeeds: Option[String] = None,
  remoteAddress: Option[String] = None,
  prefix: Option[String] = None,
  username: Option[String] = None,
  password: Option[String] = None,
  clientMode: Boolean = false
) extends Local

case object Stop extends Local

case object Nuke extends Local

case object StartNomad extends Local

sealed trait DNS extends Runtime

case object DNSUp extends DNS

case object DNSDown extends DNS

case class FlagSet(
  flags: List[String],
  enable: Boolean,
  remoteAddress: Option[String],
  username: Option[String],
  password: Option[String]
) extends Runtime

case class FlagQuery(
  remoteAddress: Option[String],
  username: Option[String],
  password: Option[String]
) extends Runtime

case class Update(
  remoteAddress: Option[String] = None,
  prefix: Option[String] = None,
  username: Option[String] = None,
  password: Option[String] = None
) extends Local

case class AddUser(name: String, roles: List[String]) extends Runtime

sealed trait Prism extends RadixCMD

case object PList extends Prism

case class PPath(path: String) extends Prism

sealed trait Oauth extends RadixCMD

case object GoogleSheets extends Oauth

case class ScriptHead(cmd: RadixCMD)

object cli {

  implicit class Weakener[F[_], A](fa: F[A])(implicit F: scalaz.Functor[F]) {
    def weaken[B](implicit ev: A <:< B): F[B] = fa.map(identity(_))
  }

  private val oauthGoogleSheets = subparser[Oauth](
    metavar("google-sheets"),
    command(
      "google-sheets",
      info(
        pure(GoogleSheets),
        progDesc("Set up a Google Sheets token")
      )
    )
  )

  private val oauth = subparser[Oauth](
    metavar("oauth"),
    command(
      "oauth",
      info(
        oauthGoogleSheets,
        progDesc("Configure OAuth tokens")
      )
    )
  )

  private val runtimeStart = subparser[Start](
    metavar("start"),
    command(
      "start",
      info(
        pure(Start()) <*>

          optional(
            strOption(
              metavar("LOGLEVEL"),
              long("debug"),
              help("Use a custom log verbosity across services")
            )
          ).map(debug => {
            exist: Start =>
              debug match {
                case Some("debug") => exist.copy(loglevel = scribe.Level.Debug)
                case Some("error") => exist.copy(loglevel = scribe.Level.Error)
                case Some("info")  => exist.copy(loglevel = scribe.Level.Info)
                case Some("trace") => exist.copy(loglevel = scribe.Level.Trace)
                case Some(_)       => throw new IllegalArgumentException(s"Verbosity must be debug, error, info, or trace")
                case None          => exist
              }
          }) <*>

          optional(
            strOption(
              metavar("IP"),
              long("force-bind-ip"),
              helpDoc(
                Some(
                  Doc.append(
                    Doc.append(Doc.text("Force services to bind to the specified ip and subnet"), Doc.linebreak),
                    Doc.text("(ex: \"192.168.1.5\")")
                  )
                )
              )
            )
          ).map(subnet => {
            exist: Start =>
              subnet match {
                case str @ Some(_) => exist.copy(bindIP = str)
                case None          => exist
              }
          }) <*>

          optional(
            strOption(
              metavar("SEEDS"),
              long("consul-seeds"),
              helpDoc(
                Some(
                  Doc.append(
                    Doc.append(Doc.text("Comma separated list of seed nodes for consul"), Doc.linebreak),
                    Doc.text("(maps to retry_join in consul.json)")
                  )
                )
              )
            )
          ).map(seeds => {
            exist: Start =>
              seeds match {
                case list @ Some(_) => exist.copy(consulSeeds = list)
                case None           => exist
              }
          }) <*>

          optional(
            strOption(
              metavar("ADDR"),
              long("remote-address"),
              help("Address to remote Consul instance")
            )
          ).map(ra => { exist: Start => exist.copy(remoteAddress = ra) }) <*>

          optional(
            strOption(
              metavar("PREFIX"),
              long("prefix"),
              help("Custom prefix for Nomad jobs")
            )
          ).map(prefix => {
            exist: Start =>
              prefix match {
                case Some(_) => exist.copy(prefix = prefix)
                case None    => exist
              }
          }) <*>

          optional(
            switch(
//              metavar("CLIENT"),
              long("client-mode"),
              help("Specify whether Nomad/Consul/Vault should operate in Client mode.")
            )
          ).map(clientMode => {
            exist: Start =>
              clientMode match {
                case Some(_ @value) => exist.copy(clientMode = value)
                case None           => exist
              }
          }) <*>

          optional(
            strOption(
              metavar("USERNAME"),
              long("username"),
              help("Remote username (set locally with add_user cmd)")
            )
          ).map(username => {
            exist: Start =>
              username match {
                case Some(_) => exist.copy(username = username)
                case None    => exist
              }
          }) <*>

          optional(
            strOption(
              metavar("PASSWORD"),
              long("password"),
              help("Remote password (set locally with add_user cmd)")
            )
          ).map(password => {
            exist: Start =>
              password match {
                case Some(_) => exist.copy(password = password)
                case None    => exist
              }
          }),
        progDesc("Start the radix core services on the current system")
      )
    )
  )

  private val runtimeStop = subparser[Stop.type](
    metavar("stop"),
    command(
      "stop",
      info(
        pure(Stop),
        progDesc("Stop services across all nodes")
      )
    )
  )

  private val runtimeNuke = subparser[Nuke.type](
    metavar("nuke"),
    command(
      "nuke",
      info(
        pure(Nuke),
        progDesc("Remove radix core services from the this node")
      )
    )
  )

  private val runtimeStartNomad = subparser[StartNomad.type](
    metavar("start_nomad"),
    command(
      "start_nomad",
      info(
        pure(StartNomad),
        progDesc("Start a nomad job")
      )
    )
  )

  private val runtimeDnsDown = subparser[DNSDown.type](
    metavar("down"),
    command("down", info(pure(DNSDown), progDesc("Remove Consul DNS from system DNS configuration")))
  )

  private val runtimeDnsUp = subparser[DNSUp.type](
    metavar("up"),
    command(
      "up",
      info(
        pure(DNSUp),
        progDesc("Add Consul DNS into system DNS configuration")
      )
    )
  )

  private val runtimeDns = subparser[DNS](
    metavar("dns"),
    command(
      "dns",
      info(
        runtimeDnsDown.weaken[DNS] <|> runtimeDnsUp.weaken[DNS],
        progDesc("configure this machine's DNS for the Radix runtime")
      )
    )
  )

  private val runtimeAddUser = subparser[AddUser](
    metavar("add_user"),
    command(
      "add_user",
      info(
        ^(
          strArgument(metavar("NAME"), help("Username (used by the --username option of other commands)")),
          many(strArgument(metavar("POLICIES..."), help("Optional list of vault policies to attach to the user")))
        )(AddUser),
        progDesc("Create a new local user for use with the --remote-address option on another machine")
      )
    )
  )

  private val runtimeEnable = subparser[FlagSet](
    metavar("enable"),
    command(
      "enable",
      info(
        ^^^(
          many(
            strArgument(
              metavar("FLAGS"),
              help("List of features/components to enable")
            )
          ),
          optional(
            strOption(
              metavar("ADDR"),
              long("remote-address"),
              help("Address to remote Consul instance")
            )
          ),
          optional(
            strOption(
              metavar("USERNAME"),
              long("username"),
              help("Remote username (set locally with add_user cmd)")
            )
          ),
          optional(
            strOption(
              metavar("PASSWORD"),
              long("password"),
              help("Remote password (set locally with add_user cmd)")
            )
          )
        )(FlagSet(_, true, _, _, _)),
        progDesc("Enable components or features of the Radix runtime")
      )
    )
  )

  private val runtimeDisable = subparser[FlagSet](
    metavar("disable"),
    command(
      "disable",
      info(
        ^^^(
          many(
            strArgument(
              metavar("FLAGS"),
              help("List of features/components to disable")
            )
          ),
          optional(
            strOption(
              metavar("ADDR"),
              long("remote-address"),
              help("Address to remote Consul instance")
            )
          ),
          optional(
            strOption(
              metavar("USERNAME"),
              long("username"),
              help("Remote username (set locally with add_user cmd)")
            )
          ),
          optional(
            strOption(
              metavar("PASSWORD"),
              long("password"),
              help("Remote password (set locally with add_user cmd)")
            )
          )
        )(FlagSet(_, false, _, _, _)),
        progDesc("Disable components or features of the Radix runtime")
      )
    )
  )

  private val runtimeQuery = subparser[FlagQuery](
    metavar("query"),
    command(
      "query",
      info(
        ^^(
          optional(
            strOption(
              metavar("ADDR"),
              long("remote-address"),
              help("Address to remote Consul instance")
            )
          ),
          optional(
            strOption(
              metavar("USERNAME"),
              long("username"),
              help("Remote username (set locally with add_user cmd)")
            )
          ),
          optional(
            strOption(
              metavar("PASSWORD"),
              long("password"),
              help("Remote password (set locally with add_user cmd)")
            )
          )
        )(FlagQuery),
        progDesc("Check which features or components are enabled and view their configuration")
      )
    )
  )

  private val runtimeUpdate = subparser[Update](
    metavar("update"),
    command(
      "update",
      info(
        pure(Update()) <*>
          optional(
            strOption(
              metavar("ADDR"),
              long("remote-address"),
              help("Address to remote Consul instance")
            )
          ).map(ra => {
            exist: Update => exist.copy(remoteAddress = ra)
          }) <*>

          optional(
            strOption(
              metavar("PREFIX"),
              long("prefix"),
              help("Custom prefix for Nomad jobs")
            )
          ).map(prefix => {
            exist: Update =>
              prefix match {
                case Some(_) => exist.copy(prefix = prefix)
                case None    => exist.copy(prefix = Some(daemonutil.getPrefix(false)))
              }
          }) <*>

          optional(
            strOption(
              metavar("USERNAME"),
              long("username"),
              help("Remote username (set locally with add_user cmd)")
            )
          ).map(username => {
            exist: Update =>
              username match {
                case Some(_) => exist.copy(username = username)
                case None    => exist
              }
          }) <*>

          optional(
            strOption(
              metavar("PASSWORD"),
              long("password"),
              help("Remote password (set locally with add_user cmd)")
            )
          ).map(password => {
            exist: Update =>
              password match {
                case Some(_) => exist.copy(password = password)
                case None    => exist
              }
          })
      )
    )
  )

  private val runtimeCmds: List[Parser[Runtime]] = List(
    runtimeStart,
    runtimeStop,
    runtimeNuke,
    runtimeStartNomad,
    runtimeDns,
    runtimeAddUser,
    runtimeEnable,
    runtimeDisable,
    runtimeQuery,
    runtimeUpdate
  ).map(_.weaken[Runtime])

  private val runtime = subparser[Runtime](
    metavar("runtime"),
    command(
      "runtime",
      info(
        runtimeCmds.reduce(_ <|> _),
        progDescDoc(
          Some(
            Doc.foldDoc(
              Seq(
                Doc.text("Radix runtime commands:"),
                Doc.linebreak,
                Doc.linebreak,
                Doc.text("To choose which components of the Radix runtime are enabled, run:"),
                Doc.linebreak,
                Doc.indent(
                  2,
                  Doc.foldDoc(
                    Seq(
                      Doc.text("timberland runtime enable"),
                      Doc.linebreak,
                      Doc.text("timberland runtime disable"),
                      Doc.linebreak
                    )
                  )(Doc.append)
                ),
                Doc.linebreak,
                Doc.text("To start the Radix runtime, run:"),
                Doc.linebreak,
                Doc.indent(2, Doc.text("timberland runtime start")),
                Doc.linebreak,
                Doc.linebreak,
                Doc.text("To view information about the current system configuration, run:"),
                Doc.linebreak,
                Doc.indent(2, Doc.text("timberland runtime query")),
                Doc.linebreak,
                Doc.linebreak,
                Doc.text("To update the components in the Radix runtime, run:"),
                Doc.linebreak,
                Doc.indent(2, Doc.text("timberland runtime update")),
                Doc.linebreak,
                Doc.linebreak,
                Doc.text("To make this Radix installation controllable from a remote machine, run:"),
                Doc.linebreak,
                Doc.indent(2, Doc.text("timberland runtime add_user")),
                Doc.linebreak,
                Doc.linebreak,
                Doc.text("To configure this machine's DNS for the Radix runtime, run:"),
                Doc.linebreak,
                Doc.indent(
                  2,
                  Doc.foldDoc(
                    Seq(
                      Doc.text("timberland runtime dns down"),
                      Doc.linebreak,
                      Doc.text("timberland runtime dns up"),
                      Doc.linebreak
                    )
                  )(Doc.append)
                ),
                Doc.linebreak,
                Doc.text("To stop or reset an existing Radix runtime installation, run:"),
                Doc.linebreak,
                Doc.indent(
                  2,
                  Doc.foldDoc(
                    Seq(
                      Doc.text("timberland runtime stop"),
                      Doc.linebreak,
                      Doc.text("timberland runtime nuke"),
                      Doc.linebreak
                    )
                  )(Doc.append)
                )
              )
            )(Doc.append)
          )
        )
      )
    )
  ) <*> helper

  val opts: ParserInfo[RadixCMD] = info(
    (runtime.weaken[RadixCMD] <|> oauth.weaken[RadixCMD]) <*> helper,
    progDescDoc(
      Some(
        Doc.foldDoc(
          Seq(
            Doc.text("Welcome to Timberland - The Radix Runtime Bootstrapper"),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To view info about starting, updating, and configuring the runtime, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland runtime")),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To view info about setting up oauth, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland oauth"))
          )
        )(Doc.append)
      )
    )
  )
}
