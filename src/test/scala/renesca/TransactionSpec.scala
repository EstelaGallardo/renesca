package renesca

import org.junit.runner.RunWith
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TransactionSpec extends Specification with Mockito {
  val statement = "match n return n"
  val jsonRequest = json.Request(List(json.Statement(statement, List("graph"))))
  val jsonRequestWithoutResult = json.Request(List(json.Statement(statement)))
  val jsonResponse = json.Response(
    commit = Some("http://localhost:7474/db/data/transaction/1/commit"),
    transaction = Some(json.Transaction("Thu, 29 Jan 2015 11:56:08 +0000"))
  )
  val jsonResponseWithError = json.Response(
    errors = List(json.Error(
      "Neo.ClientError.Statement.InvalidSyntax",
      "Invalid input 'T': expected <init> (line 1, column 1)\n\"This is not a valid Cypher Statement.\"\n ^"))
  )
  val transactionResponse = (TransactionId("1"), jsonResponse)

  def newTransaction = {
    val tx = new Transaction
    tx.restService = mock[RestService].smart

    tx.restService.singleRequest(jsonRequest) returns jsonResponse
    tx.restService.openTransaction(jsonRequest) returns transactionResponse
    tx.restService.openTransaction(jsonRequestWithoutResult) returns transactionResponse
    tx.restService.resumeTransaction(TransactionId("1"), jsonRequest) returns jsonResponse
    tx.restService.resumeTransaction(TransactionId("1"), jsonRequestWithoutResult) returns jsonResponse
    tx.restService.commitTransaction(any, any) returns json.Response()

    tx
  }

  "Transaction" should {
    "open transaction on first request" in {
      val tx = newTransaction

      tx.query(statement)
      tx.commit()

      there was one(tx.restService).openTransaction(jsonRequestWithoutResult)
      there was one(tx.restService).commitTransaction(TransactionId("1"))
      there was no(tx.restService).resumeTransaction(TransactionId("1"), jsonRequest)
    }
  }

  "open transaction on first, resume on second request" in {
    val tx = newTransaction

    tx.query(statement)
    tx.query(statement)
    tx.commit()

    there was one(tx.restService).openTransaction(jsonRequestWithoutResult)
    there was one(tx.restService).resumeTransaction(TransactionId("1"), jsonRequestWithoutResult)
    there was one(tx.restService).commitTransaction(TransactionId("1"))
  }

  "open transaction on commit" in {
    val tx = newTransaction

    tx.commit.queryGraphs(statement)

    there was one(tx.restService).singleRequest(jsonRequest)
    there was no(tx.restService).openTransaction(jsonRequest)
    there was no(tx.restService).commitTransaction(TransactionId("1"))
    there was no(tx.restService).resumeTransaction(TransactionId("1"), jsonRequest)
  }

  "invalidate transaction on commit" in {
    val tx = newTransaction

    tx.commit()

    tx.isValid mustEqual false
  }

  "invalidate transaction on commit with query" in {
    val tx = newTransaction

    tx.commit.queryGraphs(statement)

    tx.isValid mustEqual false
  }

  "throw exception when using invalidated transaction" in {
    val tx = newTransaction
    tx.invalidate()

    tx.queryGraph(statement) must throwA[RuntimeException]
    tx.commit() must throwA[RuntimeException]
    tx.commit.queryGraphs(statement) must throwA[RuntimeException]
  }

  "error in transaction rollbacks transaction and throws exception" in {
    val tx = new Transaction
    tx.restService = mock[RestService]
    tx.restService.openTransaction(jsonRequest) returns ((TransactionId("1"), jsonResponseWithError))

    tx.queryGraph(statement) must throwA[RuntimeException]

    tx.isValid mustEqual false
    there was one(tx.restService).rollbackTransaction(TransactionId("1"))
  }

  "don't commit when doing nothing in transaction" in {
    val tx = newTransaction
    tx.commit()

    there was no(tx.restService).singleRequest(any)
    there was no(tx.restService).openTransaction(any)
    there was no(tx.restService).commitTransaction(any, any)
    there was no(tx.restService).resumeTransaction(any, any)
    there was no(tx.restService).rollbackTransaction(any)
  }

}

