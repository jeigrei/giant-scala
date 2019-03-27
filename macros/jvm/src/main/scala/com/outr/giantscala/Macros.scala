package com.outr.giantscala

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("Enable macros to expand")
object Macros {
  def auto[T](c: blackbox.Context)(implicit t: c.WeakTypeTag[T]): c.Tree = {
    import c.universe._

    q"""
       import _root_.org.mongodb.scala.bson.collection.immutable.Document
       import _root_.profig.JsonUtil

       new Converter[$t] {
         override def toDocument(t: $t): Document = {
           Document(JsonUtil.toJsonString(t))
         }

         override def fromDocument(document: Document): $t = {
           JsonUtil.fromJsonString[$t](document.toJson(Converter.settings))
         }
       }
     """
  }

  def storeTyped[T](c: blackbox.Context)(key: c.Tree)(implicit t: c.WeakTypeTag[T]): c.Tree = {
    import c.universe._

    val store = c.prefix

    q"""
       import _root_.profig.JsonUtil

       new _root_.com.outr.giantscala.TypedStore[$t] {
         override def get: Future[Option[$t]] = scribe.async {
           $store.string.get($key).map(_.map(json => JsonUtil.fromJsonString[$t](json)))
         }

         override def apply(default: => $t): Future[$t] = scribe.async(get.map(_.getOrElse(default)))

         override def set(value: $t): Future[Unit] = scribe.async {
           val json = JsonUtil.toJsonString[$t](value)
           $store.string.set($key, json)
         }
       }
     """
  }

  def aggregateAs[T](c: blackbox.Context)(implicit t: c.WeakTypeTag[T]): c.Tree = {
    import c.universe._

    q"""
       ${c.prefix}.as[$t](_root_.com.outr.giantscala.Converter.auto[$t])
     """
  }

  def aggregateReplaceRoot[T](c: blackbox.Context)(field: c.Tree)(implicit t: c.WeakTypeTag[T]): c.Tree = {
    import c.universe._

    q"""
       ${c.prefix}.as[$t](_root_.com.outr.giantscala.Converter.auto[$t]).replaceRoot(_root_.io.circe.Json.fromString($field.fieldName))
     """
  }
}