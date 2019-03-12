package com.outr.giantscala.dsl

import com.outr.giantscala.{DBCollection, ModelObject}
import io.circe.{Json, Printer}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Collation, ReplaceOptions}
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContext, Future}

case class ReplaceOneBuilder[Type <: ModelObject](collection: DBCollection[Type],
                                                  mongoCollection: MongoCollection[Document],
                                                  replacement: Type,
                                                  conditions: List[MatchCondition] = Nil,
                                                  _upsert: Boolean = false,
                                                  _bypassDocumentValidations: Boolean = false,
                                                  _collation: Option[Collation] = None) {
  def `match`(conditions: MatchCondition*): ReplaceOneBuilder[Type] = {
    copy(conditions = this.conditions ::: conditions.toList)
  }
  def filter(conditions: MatchCondition*): ReplaceOneBuilder[Type] = `match`(conditions: _*)
  def upsert: ReplaceOneBuilder[Type] = copy(_upsert = true)
  def bypassDocumentValidations: ReplaceOneBuilder[Type] = copy(_bypassDocumentValidations = true)
  def collation(collation: Collation): ReplaceOneBuilder[Type] = copy(_collation = Some(collation))

  def toFuture(implicit executionContext: ExecutionContext): Future[UpdateResult] = {
    val filter = Document(conditions.map(_.json).foldLeft(Json.obj())((j1, j2) => j1.deepMerge(j2)).pretty(Printer.spaces2))
    val document = collection.converter.toDocument(replacement)
    val options = new ReplaceOptions()
    if (_upsert) options.upsert(_upsert)
    if (_bypassDocumentValidations) options.bypassDocumentValidation(_bypassDocumentValidations)
    _collation.foreach(options.collation)
    mongoCollection.replaceOne(filter, document, options).toFuture()
  }
}