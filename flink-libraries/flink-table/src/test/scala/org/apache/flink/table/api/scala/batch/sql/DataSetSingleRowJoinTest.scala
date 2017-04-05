/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.api.scala.batch.sql

import org.apache.flink.api.scala._
import org.apache.flink.table.api.scala._
import org.apache.flink.table.utils.TableTestBase
import org.apache.flink.table.utils.TableTestUtil._
import org.junit.Test

class DataSetSingleRowJoinTest extends TableTestBase {

  @Test
  def testSingleRowJoinWithCalcInput(): Unit = {
    val util = batchTestUtil()
    util.addTable[(Int, Int)]("A", 'a1, 'a2)

    val query =
      "SELECT a1, asum " +
      "FROM A, (SELECT sum(a1) + sum(a2) AS asum FROM A)"

    val expected =
      binaryNode(
        "DataSetSingleRowJoin",
        unaryNode(
          "DataSetCalc",
          batchTableNode(0),
          term("select", "a1")
        ),
        unaryNode(
          "DataSetCalc",
          unaryNode(
            "DataSetAggregate",
            unaryNode(
              "DataSetUnion",
              unaryNode(
                "DataSetValues",
                batchTableNode(0),
                tuples(List(null, null)),
                term("values", "a1", "a2")
              ),
              term("union","a1","a2")
            ),
            term("select", "SUM(a1) AS $f0", "SUM(a2) AS $f1")
          ),
          term("select", "+($f0, $f1) AS asum")
        ),
        term("where", "true"),
        term("join", "a1", "asum"),
        term("joinType", "NestedLoopJoin")
      )

    util.verifySql(query, expected)
  }

  @Test
  def testSingleRowEquiJoin(): Unit = {
    val util = batchTestUtil()
    util.addTable[(Int, String)]("A", 'a1, 'a2)

    val query =
      "SELECT a1, a2 " +
      "FROM A, (SELECT count(a1) AS cnt FROM A) " +
      "WHERE a1 = cnt"

    val expected =
      unaryNode(
        "DataSetCalc",
        binaryNode(
          "DataSetSingleRowJoin",
          batchTableNode(0),
          unaryNode(
            "DataSetAggregate",
            unaryNode(
              "DataSetUnion",
              unaryNode(
                "DataSetValues",
                unaryNode(
                  "DataSetCalc",
                  batchTableNode(0),
                  term("select", "a1")
                ),
                tuples(List(null)),
                term("values", "a1")
              ),
              term("union","a1")
            ),
            term("select", "COUNT(a1) AS cnt")
          ),
          term("where", "=(CAST(a1), cnt)"),
          term("join", "a1", "a2", "cnt"),
          term("joinType", "NestedLoopJoin")
        ),
        term("select", "a1", "a2")
      )

    util.verifySql(query, expected)
  }

  @Test
  def testSingleRowNotEquiJoin(): Unit = {
    val util = batchTestUtil()
    util.addTable[(Int, String)]("A", 'a1, 'a2)

    val query =
      "SELECT a1, a2 " +
      "FROM A, (SELECT count(a1) AS cnt FROM A) " +
      "WHERE a1 < cnt"

    val expected =
      unaryNode(
        "DataSetCalc",
        binaryNode(
          "DataSetSingleRowJoin",
          batchTableNode(0),
          unaryNode(
            "DataSetAggregate",
            unaryNode(
              "DataSetUnion",
              unaryNode(
                "DataSetValues",
                unaryNode(
                  "DataSetCalc",
                  batchTableNode(0),
                  term("select", "a1")
                ),
                tuples(List(null)),
                term("values", "a1")
              ),
              term("union", "a1")
            ),
            term("select", "COUNT(a1) AS cnt")
          ),
          term("where", "<(a1, cnt)"),
          term("join", "a1", "a2", "cnt"),
          term("joinType", "NestedLoopJoin")
        ),
        term("select", "a1", "a2")
      )

    util.verifySql(query, expected)
  }

  @Test
  def testSingleRowJoinWithComplexPredicate(): Unit = {
    val util = batchTestUtil()
    util.addTable[(Int, Long)]("A", 'a1, 'a2)
    util.addTable[(Int, Long)]("B", 'b1, 'b2)

    val query =
      "SELECT a1, a2, b1, b2 " +
        "FROM A, (SELECT min(b1) AS b1, max(b2) AS b2 FROM B) " +
        "WHERE a1 < b1 AND a2 = b2"

    val expected = binaryNode(
      "DataSetSingleRowJoin",
      batchTableNode(0),
      unaryNode(
        "DataSetAggregate",
        unaryNode(
          "DataSetUnion",
          unaryNode(
            "DataSetValues",
            batchTableNode(1),
            tuples(List(null, null)),
            term("values", "b1", "b2")
          ),
          term("union","b1","b2")
        ),
        term("select", "MIN(b1) AS b1", "MAX(b2) AS b2")
      ),
      term("where", "AND(<(a1, b1)", "=(a2, b2))"),
      term("join", "a1", "a2", "b1", "b2"),
      term("joinType", "NestedLoopJoin")
    )

    util.verifySql(query, expected)
  }

  @Test
  def testSingleRowJoinLeftOuterJoin(): Unit = {
    val util = batchTestUtil()
    util.addTable[(Int, Int)]("A", 'a1, 'a2)
    util.addTable[(Int, Int)]("B", 'b1, 'b2)

    val queryLeftJoin =
      "SELECT a2 FROM A " +
        "LEFT JOIN " +
        "(SELECT COUNT(*) AS cnt FROM B) " +
        "AS x " +
        "ON a1 < cnt"

    val expected =
      unaryNode(
        "DataSetCalc",
        unaryNode(
          "DataSetSingleRowJoin",
          batchTableNode(0),
          term("where", "<(a1, cnt)"),
          term("join", "a1", "a2", "cnt"),
          term("joinType", "NestedLoopJoin")
        ),
        term("select", "a2")
      ) + "\n" +
        unaryNode(
          "DataSetAggregate",
          unaryNode(
            "DataSetUnion",
            unaryNode(
              "DataSetValues",
              unaryNode(
                "DataSetCalc",
                batchTableNode(1),
                term("select", "0 AS $f0")),
              tuples(List(null)), term("values", "$f0")
            ),
            term("union", "$f0")
          ),
          term("select", "COUNT(*) AS cnt")
        )

    util.verifySql(queryLeftJoin, expected)
  }

  @Test
  def testSingleRowJoinRightOuterJoin(): Unit = {
    val util = batchTestUtil()
    util.addTable[(Int, Int)]("A", 'a1, 'a2)
    util.addTable[(Int, Int)]("B", 'b1, 'b2)

    val queryRightJoin =
      "SELECT a2 FROM A " +
        "RIGHT JOIN " +
        "(SELECT COUNT(*) AS cnt FROM B) " +
        "AS x " +
        "ON a1 < cnt"

    //val queryRightJoin =
    //  "SELECT a2 FROM (SELECT COUNT(*) AS cnt FROM B) RIGHT JOIN A ON a1 < cnt"

    val expected =
      unaryNode(
        "DataSetCalc",
        unaryNode(
          "DataSetSingleRowJoin",
          batchTableNode(0),
          term("where", "<(a1, cnt)"),
          term("join", "a1", "a2", "cnt"),
          term("joinType", "NestedLoopJoin")
        ),
        term("select", "a2")
      ) + "\n" +
        unaryNode(
          "DataSetAggregate",
          unaryNode(
            "DataSetUnion",
            unaryNode(
              "DataSetValues",
              unaryNode(
                "DataSetCalc",
                batchTableNode(1),
                term("select", "0 AS $f0")),
              tuples(List(null)), term("values", "$f0")
            ),
            term("union", "$f0")
          ),
          term("select", "COUNT(*) AS cnt")
        )

    util.verifySql(queryRightJoin, expected)
  }

  @Test
  def testSingleRowJoinInnerJoin(): Unit = {
    val util = batchTestUtil()
    util.addTable[(Int, Int)]("A", 'a1, 'a2)
    val query =
      "SELECT a2, sum(a1) " +
        "FROM A " +
        "GROUP BY a2 " +
        "HAVING sum(a1) > (SELECT sum(a1) * 0.1 FROM A)"

    val expected =
      unaryNode(
        "DataSetCalc",
        unaryNode(
          "DataSetSingleRowJoin",
          unaryNode(
            "DataSetAggregate",
            batchTableNode(0),
            term("groupBy", "a2"),
            term("select", "a2", "SUM(a1) AS EXPR$1")
          ),
          term("where", ">(EXPR$1, EXPR$0)"),
          term("join", "a2", "EXPR$1", "EXPR$0"),
          term("joinType", "NestedLoopJoin")
        ),
        term("select", "a2", "EXPR$1")
      ) + "\n" +
        unaryNode(
          "DataSetCalc",
          unaryNode(
            "DataSetAggregate",
            unaryNode(
              "DataSetUnion",
              unaryNode(
                "DataSetValues",
                unaryNode(
                  "DataSetCalc",
                  batchTableNode(0),
                  term("select", "a1")
                ),
                tuples(List(null)), term("values", "a1")
              ),
              term("union", "a1")
            ),
            term("select", "SUM(a1) AS $f0")
          ),
          term("select", "*($f0, 0.1) AS EXPR$0")
        )

    util.verifySql(query, expected)
  }
}
