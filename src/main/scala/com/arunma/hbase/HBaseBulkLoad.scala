package com.arunma.hbase

import com.twitter.bijection.Injection
import com.twitter.bijection.avro.GenericAvroCodecs
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.commons.codec.digest.DigestUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{LongType, StringType, StructField, StructType}

import scala.io.Source

object HBaseBulkLoad {

  def main(args: Array[String]): Unit = {

    //case class EventCell (cf: String, cq: String, cv: String)
    case class EventClass(Id: String,
                          eventId: String,
                          docType: String,
                          partName: String,
                          partNumber: String,
                          version: Long,
                          payload: String)

    val columns = List(
      "Id",
      "eventId",
      "docType",
      "partName",
      "partNumber",
      "version",
      "payload"
    )

    val spark = SparkSession
      .builder()
      .appName("HBaseBulk")
      .master("local[*]")
      .getOrCreate()

    val structType = StructType(List(
      StructField("Id", StringType),
      StructField("eventId", StringType),
      StructField("docType", StringType),
      StructField("partName", StringType),
      StructField("partNumber", StringType),
      StructField("version", LongType),
      StructField("payload", StringType)
    ))

    val dataset = spark
      .read
      .option("header", false)
      .schema(structType)
      .csv("/Users/arunma/IdeaProjects/SparkDataSamples/src/main/resources/1/omneo.csv")
      .as[EventClass]

    val schema = Schema.parse(Source.fromFile("/Users/arunma/IdeaProjects/SparkDataSamples/src/main/resources/1/omneo.avsc").mkString)
    val injection:Injection[GenericRecord, Array[Byte]] = GenericAvroCodecs.toBinary(schema)

    val bytesRdd = dataset.map { event =>
      val record = new GenericData.Record(schema)
      val eventIdBytes = DigestUtils.md5(event.Id)

      record.put("Id", event.Id)
      record.put("eventId", event.eventId)
      record.put("docType", event.docType)
      record.put("partName", event.partName)
      record.put("partNumber", event.partNumber)
      record.put("version", event.version)
      record.put("payload", event.payload)

      val recordBytes = injection.apply(record)
      (eventIdBytes, recordBytes)
    }



    dataset
      .write
      .format("com.databricks.spark.avro")
      .save("/Users/arunma/IdeaProjects/SparkDataSamples/src/main/resources/avro")


  }

}
