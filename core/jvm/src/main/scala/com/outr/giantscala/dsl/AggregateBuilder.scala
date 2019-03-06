package com.outr.giantscala.dsl

import java.util.concurrent.atomic.AtomicInteger

import com.outr.giantscala._
import io.circe.{Json, Printer}
import org.mongodb.scala.Observer
import org.mongodb.scala.bson.collection.immutable.Document
import reactify.Channel

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.experimental.macros

case class AggregateBuilder[Type <: ModelObject, Out](collection: DBCollection[Type],
                                                      converter: Converter[Out],
                                                      pipeline: List[AggregateInstruction] = Nil) {
  def withPipeline(instructions: AggregateInstruction*): AggregateBuilder[Type, Out] = {
    copy(pipeline = pipeline ::: instructions.toList)
  }
  def `match`(conditions: MatchCondition*): AggregateBuilder[Type, Out] = {
    withPipeline(AggregateMatch(conditions.toList))
  }
  def filter(conditions: MatchCondition*): AggregateBuilder[Type, Out] = {
    withPipeline(AggregateMatch(conditions.toList))
  }
  def project(fields: ProjectField*): AggregateBuilder[Type, Out] = withPipeline(AggregateProject(fields.toList))
  def group(fields: ProjectField*): AggregateBuilder[Type, Out] = withPipeline(AggregateGroup(fields.toList))
  def sample(size: Int): AggregateBuilder[Type, Out] = withPipeline(AggregateSample(size))
  def lookup[Other <: ModelObject, T](from: DBCollection[Other],
                                      localField: Field[T],
                                      foreignField: Field[T],
                                      as: String): AggregateBuilder[Type, Out] = {
    withPipeline(AggregateLookup(from, localField, foreignField, as))
  }
  def replaceRoot[T](field: Field[T]): AggregateBuilder[Type, T] = macro Macros.aggregateReplaceRoot[T]
  def replaceRoot(json: Json): AggregateBuilder[Type, Out] = withPipeline(AggregateReplaceRoot(json))
  def replaceRoot(field: ProjectField): AggregateBuilder[Type, Out] = replaceRoot(field.json)
  def addFields(fields: ProjectField*): AggregateBuilder[Type, Out] = withPipeline(AggregateAddFields(fields.toList))
  def unwind(path: String): AggregateBuilder[Type, Out] = withPipeline(AggregateUnwind(path))
  def skip(skip: Int): AggregateBuilder[Type, Out] = withPipeline(AggregateSkip(skip))
  def limit(limit: Int): AggregateBuilder[Type, Out] = withPipeline(AggregateLimit(limit))
  def sort(sortFields: SortField*): AggregateBuilder[Type, Out] = withPipeline(AggregateSort(sortFields.toList))
  def count(): AggregateBuilder[Type, Int] = withPipeline(AggregateCount("countResult")).as[Int](Count)
  def out(collectionName: String): AggregateBuilder[Type, Out] = withPipeline(AggregateOut(collectionName))

  def opt[T](option: Option[T])
            (f: (AggregateBuilder[Type, Out], T) => AggregateBuilder[Type, Out]): AggregateBuilder[Type, Out] = {
    option.map(t => f(this, t)).getOrElse(this)
  }

  def as[T](converter: Converter[T]): AggregateBuilder[Type, T] = copy(converter = converter)
  def as[T]: AggregateBuilder[Type, T] = macro Macros.aggregateAs[T]

  def json: List[Json] = pipeline.map(_.json)
  def jsonStrings: List[String] = json.map(_.pretty(Printer.spaces2))
  def documents: List[Document] = jsonStrings.map(Document.apply)

  def toQuery(includeSpaces: Boolean = true): String = {
    val printer = if (includeSpaces) {
      Printer.spaces2
    } else {
      Printer.noSpaces
    }
    s"db.${collection.collectionName}.aggregate(${Json.arr(json: _*).pretty(printer)})"
  }
  def toFuture(implicit executionContext: ExecutionContext): Future[List[Out]] = {
    collection.collection.aggregate(documents).toFuture().map(_.map(converter.fromDocument).toList)
  }
  def toStream(channel: Channel[Out]): Future[Int] = {
    val promise = Promise[Int]
    val counter = new AtomicInteger(0)
    collection.collection.aggregate(documents).subscribe(new Observer[Document] {
      override def onNext(result: Document): Unit = {
        channel := converter.fromDocument(result)
        counter.incrementAndGet()
      }

      override def onError(t: Throwable): Unit = promise.failure(t)

      override def onComplete(): Unit = promise.success(counter.get())
    })
    promise.future
  }
}