package com.martinsnyder.chatserver

import java.io.File

import cats.effect.{ContextShift, Sync}
import fs2.Stream
import fs2.Pipe
import fs2.concurrent.{Queue, Topic}
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import scala.concurrent.ExecutionContext.global


class HelloWorldRoutes[F[_]: Sync: ContextShift](queue: Queue[F, InputMessage], topic: Topic[F, OutputMessage]) extends Http4sDsl[F] {
  val routes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case request @ GET -> Root  => StaticFile.fromFile(new File("static/index.html"), global, Some(request)).getOrElseF(NotFound())
      case request @ GET -> Root / "chat.js"  => StaticFile.fromFile(new File("static/chat.js"), global, Some(request)).getOrElseF(NotFound())

      case GET -> Root / "ws" / userName =>
        val toClient: Stream[F, WebSocketFrame.Text] =
          topic
            .subscribe(1000)
            .filter(_.forUser(userName))
            .map(msg => Text(msg.toString))


        val fromClient: Pipe[F, WebSocketFrame, Unit] =
          _.map({case Text(text, _) => InputMessage.parse(userName, text) })
           .through(queue.enqueue)

        WebSocketBuilder[F].build(toClient, fromClient)
    }
}