import zio._

object Main extends zio.App {
  // Very contrived reproducer for the memory leak that seems to be when Fiber.join is raced
  // Takes less than a minute to leak 100MB of heap space. Uses ZIO 1.0.1
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  (for {
    channel <- Queue.bounded[Promise[Throwable, Unit]](1)
    queueListenerFiber <- (for {
      responsePromise <- channel.take
      _ <- Task.unit.to(responsePromise)
    } yield ())
      .forever
      .fork
    enqueueF = for {
      deferred <- Promise.make[Throwable, Unit]
      _ <- channel.offer(deferred)
      output <- queueListenerFiber.join.unit.race(deferred.await)
    } yield output
    _ <- enqueueF
      .repeat(Schedule.forever.delayed(_ => zio.duration.Duration.fromMillis(1))) // Delay here to make it easier to monitor in VisualVM, etc. Not required to reproduce
    _ <- ZIO.infinity
  } yield ()).exitCode
}
