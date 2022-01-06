package com.radix.timberland

import com.radix.timberland.launch.daemonutil
import com.radix.timberland.util.{EmailRecipient, MTeamsRecipient, MessageKind, SlackRecipient}
import io.circe.{Parser => _}
import optparse_applicative._
import optparse_applicative.types.{Doc, Parser}
import scalaz.syntax.apply._

sealed trait RadixCMD

case class Start(
  loglevel: scribe.Level = scribe.Level.Debug,
  bindIP: Option[String] = None,
  leaderNode: Option[String] = None,
  remoteAddress: Option[String] = None,
  namespace: Option[String] = None,
  username: Option[String] = None,
  password: Option[String] = None,
  serverMode: Boolean = false
) extends RadixCMD

case class Env(fish: Boolean) extends RadixCMD

case object AfterStartup extends RadixCMD

case object Stop extends RadixCMD

case object Nuke extends RadixCMD

case object StartNomad extends RadixCMD

sealed trait DNS extends RadixCMD

case object DNSUp extends DNS

case object DNSDown extends DNS

case class FlagSet(
  flags: List[String],
  enable: Boolean,
  remoteAddress: Option[String],
  username: Option[String],
  password: Option[String]
) extends RadixCMD

case class FlagQuery(
  remoteAddress: Option[String],
  username: Option[String],
  password: Option[String]
) extends RadixCMD

case class Update(
  remoteAddress: Option[String] = None,
  namespace: Option[String] = None,
  username: Option[String] = None,
  password: Option[String] = None
) extends RadixCMD

case class AddUser(name: String, roles: List[String]) extends RadixCMD

case class AddRecipient(identifier: String, medium: MessageKind) extends RadixCMD

sealed trait Oauth extends RadixCMD

case object GoogleSheets extends Oauth

case class ScriptHead(cmd: RadixCMD)

object cli {

  implicit class Weakener[F[_], A](fa: F[A])(implicit F: scalaz.Functor[F]) {
    def weaken[B](implicit ev: A <:< B): F[B] = F.map(fa)(identity(_))
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

  private val env = subparser[Env](
    metavar("env"),
    command(
      "env",
      info(
        switch(long("fish"), help("Output script for fish rather than POSIX-compliant shells")).map(Env),
        progDesc("Print a shell script that puts timberland, nomad, consul, vault, and terraform in $PATH")
      )
    )
  )

  private val start = subparser[Start](
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
              metavar("LEADER"),
              long("leader-node"),
              helpDoc(
                Some(
                  Doc.append(
                    Doc.append(Doc.text("Leader node for Consul and Nomad."), Doc.linebreak),
                    Doc.text("(maps to retry_join in nomad/consul)")
                  )
                )
              )
            )
          ).map(seeds => {
            exist: Start =>
              seeds match {
                case list @ Some(_) => exist.copy(leaderNode = list)
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
              metavar("NAMESPACE"),
              long("namespace"),
              help("Namespace for Nomad jobs")
            )
          ).map(namespace => {
            exist: Start =>
              namespace match {
                case Some(_) => exist.copy(namespace = namespace)
                case None    => exist
              }
          }) <*>

          optional(
            switch(
              long("server-mode"),
              help("Specify whether Nomad/Consul/Vault should operate in Server mode.")
            )
          ).map(serverMode => {
            exist: Start =>
              serverMode match {
                case Some(_ @value) => exist.copy(serverMode = value)
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

  private val afterStartup = subparser[AfterStartup.type](
    metavar("after_startup"),
    command(
      "after_startup",
      info(
        pure(AfterStartup),
        progDesc("Unseals vault and makes sure all hashicorp services are running")
      )
    )
  )

  private val stop = subparser[Stop.type](
    metavar("stop"),
    command(
      "stop",
      info(
        pure(Stop),
        progDesc("Stop services across all nodes")
      )
    )
  )

  private val nuke = subparser[Nuke.type](
    metavar("nuke"),
    command(
      "nuke",
      info(
        pure(Nuke),
        progDesc("Remove radix core services from the this node")
      )
    )
  )

  private val startNomad = subparser[StartNomad.type](
    metavar("start_nomad"),
    command(
      "start_nomad",
      info(
        pure(StartNomad),
        progDesc("Start a nomad job")
      )
    )
  )

  private val dnsDown = subparser[DNSDown.type](
    metavar("down"),
    command("down", info(pure(DNSDown), progDesc("Remove Consul DNS from system DNS configuration")))
  )

  private val dnsUp = subparser[DNSUp.type](
    metavar("up"),
    command(
      "up",
      info(
        pure(DNSUp),
        progDesc("Add Consul DNS into system DNS configuration")
      )
    )
  )

  private val dns = subparser[DNS](
    metavar("dns"),
    command(
      "dns",
      info(
        dnsDown.weaken[DNS] <|> dnsUp.weaken[DNS],
        progDesc("configure this machine's DNS for the Radix runtime")
      )
    )
  )

  private val addUser = subparser[AddUser](
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

  private val enable = subparser[FlagSet](
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

  private val disable = subparser[FlagSet](
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

  private val query = subparser[FlagQuery](
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

  private val update = subparser[Update](
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
              metavar("NAMESPACE"),
              long("namespace"),
              help("Namespace for Nomad jobs")
            )
          ).map(namespace => {
            exist: Update =>
              namespace match {
                case Some(_) => exist.copy(namespace = namespace)
                case None    => exist.copy(namespace = Some(daemonutil.getNamespace(false)))
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

  private val addMessageRecipients = subparser[AddRecipient](
    command(
      "slack",
      info(
        ^(strArgument(metavar("ID")), strArgument(metavar("WEBHOOK")))((id, h) => AddRecipient(id, SlackRecipient(h)))
      )
    ),
    command(
      "mteams",
      info(
        ^(strArgument(metavar("ID")), strArgument(metavar("WEBHOOK")))((id, h) => AddRecipient(id, MTeamsRecipient(h)))
      )
    ),
    command(
      "email",
      info(
        ^^^(
          strArgument(metavar("ID")),
          strArgument(metavar("USERNAME")),
          strArgument(metavar("DOMAIN")),
          strArgument(metavar("PASSWORD"))
        )((id, un, dom, pwd) => AddRecipient(id, EmailRecipient(un, dom, pwd)))
      )
    )
  )

  private val cmds: List[Parser[RadixCMD]] = List(
    oauth,
    start,
    stop,
    nuke,
    env,
    startNomad,
    afterStartup,
    dns,
    addUser,
    enable,
    disable,
    query,
    update
  ).map(_.weaken[RadixCMD])

  val opts: ParserInfo[RadixCMD] = info(
    cmds.reduce(_ <|> _) <*> helper,
    progDescDoc(
      Some(
        Doc.foldDoc(
          Seq(
            Doc.text("Welcome to Timberland - The Radix Runtime Bootstrapper"),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To choose which components of the Radix runtime are enabled, run:"),
            Doc.linebreak,
            Doc.indent(
              2,
              Doc.foldDoc(
                Seq(
                  Doc.text("timberland enable"),
                  Doc.linebreak,
                  Doc.text("timberland disable"),
                  Doc.linebreak
                )
              )(Doc.append)
            ),
            Doc.linebreak,
            Doc.text("To start the Radix runtime, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland start")),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To view information about the current system configuration, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland query")),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To print shell commands for including timberland and hashicorp services in $PATH, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland runtime env")),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To update the components in the Radix runtime, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland update")),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To make this Radix installation controllable from a remote machine, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland add_user")),
            Doc.linebreak,
            Doc.linebreak,
            Doc.text("To configure this machine's DNS for the Radix runtime, run:"),
            Doc.linebreak,
            Doc.indent(
              2,
              Doc.foldDoc(
                Seq(
                  Doc.text("timberland dns down"),
                  Doc.linebreak,
                  Doc.text("timberland dns up"),
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
                  Doc.text("timberland stop"),
                  Doc.linebreak,
                  Doc.text("timberland nuke"),
                  Doc.linebreak
                )
              )(Doc.append)
            ),
            Doc.text("To view info about setting up oauth, run:"),
            Doc.linebreak,
            Doc.indent(2, Doc.text("timberland oauth"))
          )
        )(Doc.append)
      )
    )
  )
}
