package com.radix.utils

import cats.{~>, Monad}

package object helm {
  type Err = String // YOLO
  type Key = String

  def run[F[_]: Monad, A](interpreter: ConsulOp ~> F, op: ConsulOp.ConsulOpF[A]): F[A] =
    op.foldMap(interpreter)
  def runNomad[F[_]: Monad, A](interpreter: NomadOp ~> F, op: NomadOp.NomadOpF[A]): F[A] =
    op.foldMap(interpreter)
}
