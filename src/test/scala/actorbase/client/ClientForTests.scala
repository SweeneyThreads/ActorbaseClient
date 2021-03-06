package actorbase.client

/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2016 SWEeneyThreads
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * <p/>
 *
 * @author SWEeneyThreads
 * @version 0.0.1
 * @since 0.0.1
 */

import actorbase.driver.{Connection, Driver}

import scala.util.matching.Regex

/**
  * Created by kurt on 01/06/2016.
  */
object ClientForTests {
  var connection: Connection = null
  var printOutput: Boolean = true

  def main(args: Array[String]): Unit = {
    Welcome.printWelcomeMessage
    // Readline loop
    print("> ")
    for (ln <- io.Source.stdin.getLines) {
      ln match {
        case "autoconn" => executeLine("connect localhost:8181 admin admin")
        case "output_off" => {
          println("standard output OFF")
          ClientForTests.printOutput = false
        }
        case "output_on" => {
          println("standard output ON")
          ClientForTests.printOutput = true
        }
        case "overload_test1" => executeOverloadTest1(25)
        //case ""
        case _ =>  {
          var m = getMatch("createmap_test\\s(\\S+)\\s([0-9]+)$".r, ln)
          if(m != null) {
            println ("creating "+m.group(2)+" maps...")
            val before: Long = System.currentTimeMillis
            for (i <- 1 to m.group(2).toInt)
              executeLine("createmap " + m.group(1) + i)
            val after: Long = System.currentTimeMillis
            formatTime(after-before)
          } else {
            m = getMatch("insert_test\\s(\\S+)\\s(\\S+)\\s([0-9]+)$".r,ln)
            if (m != null) {
             insert_test(m.group(1),m.group(2),m.group(3).toInt)
            } else {
              m = getMatch("createdb_test\\s(\\S+)\\s([0-9]+)$".r,ln)
              if (m!=null) {
                println("creating " + m.group(2) + " database...")
                val before: Long = System.currentTimeMillis
                for (i <- 1 to m.group(2).toInt)
                  executeLine("createdb " + m.group(1) + i)
                val after: Long = System.currentTimeMillis
                formatTime(after-before)
              } else {
                m = getMatch("find_test\\s(\\S+)\\s([0-9]+)$".r,ln)
                if (m!=null) {
                  find_test(m.group(1),m.group(2).toInt)
                } else {
                  m = getMatch("insertfind_test\\s(\\S+)\\s(\\S+)\\s([0-9]+)$".r,ln)
                  if (m!=null) {
                    insert_test(m.group(1), m.group(2), m.group(3).toInt)
                    find_test(m.group(1), m.group(3).toInt)
                  } else {
                    executeLine(ln.trim)
                  }
                }
              }
            }
          }
        }
      }
      print("> ")
    }
  }

  /**
    * Processes a command line content executing the client-side query.
    * If the connection is not established checkLogin method is called.
    *
    * @param ln the query String to be processed
    */
  def executeLine(ln: String): Unit = {
    if(ln == "quit") {
      System.exit(1)
    }
    // Check if the client is connected
    if (connection != null) {
      // Close the connection if the the command is 'disconnect'
      if (ln == "disconnect") {
        connection.closeConnection()
        connection = null
        println("You are disconnected!")
      }
      // execute the query otherwise
      else {
        val out=connection.executeQuery(ln)
        if (ClientForTests.printOutput) println(out)
      }
    }
    else checkLogin(ln)
  }

  /**
    * Tries to establish a connection in case of a login query, prints an error message otherwise.
    *
    * @param ln The query String to be processed
    */
  def checkLogin(ln:String): Unit = {
    // Connection command pattern (connect address:port username password)
    val pattern = "connect\\s(\\S+):([0-9]*)\\s(\\S+)\\s(\\S+)$".r
    val result = pattern.findFirstMatchIn(ln)
    // If it's a connection command
    if (result.isDefined) {
      val regex = result.get
      connection = Driver.connect(regex.group(1), Integer.parseInt(regex.group(2)), regex.group(3), regex.group(4))
      if (connection != null) {
        println("You are connected!")
      }
      else {
        println("Connection failed")
      }
    }
    else {
      println("Please connect first")
    }
  }


  private def getMatch(pattern:Regex, command:String): Regex.Match = {
    val result = pattern.findFirstMatchIn(command)
    if (result.isDefined) return result.get
    return null
  }

  private def formatTime(time: Long) : Unit = {
    val mins=time/60000
    val secs=(time-60000*mins)/1000
    val millis=time%1000
    println (s"in: ${mins}min $secs,${millis}s")
  }

  private def executeOverloadTest1(K:Int): Unit = {
    val start: Long = System.currentTimeMillis
    print ("creating a couple of maps...\t")
    val mapNames = new Array[String] (5)
    for (i<- 0 to 4) {
      val newMap = s"testMap${System.currentTimeMillis}$i"
      mapNames(i) = newMap
      executeLine(s"createmap $newMap")
    }

    val mapsCreated: Long = System.currentTimeMillis
    formatTime(mapsCreated-start)

    print (s"\nnow inserting $K key-value tuples in each map...\t")
    for (map<-mapNames) {
      executeLine(s"selectmap $map")
      for (i<-1 to K)
        executeLine(s"insert 'key$i' value$i")
    }

    val tuplesInserted: Long = System.currentTimeMillis
    formatTime(tuplesInserted-mapsCreated)

    print ("\nnow finding each value inserted...\t")
    for (map <- mapNames) {
      executeLine(s"selectmap $map")
      for (i<-1 to K)
        executeLine(s"find 'key$i'")
    }

    val keyFound: Long = System.currentTimeMillis
    formatTime(keyFound-tuplesInserted)

    print ("\nTotal time= ")
    formatTime(keyFound-start)

  }


  def bigMapGenerator(mapName: String, i: Int) = {
    executeLine(s"cretemap $mapName")
    for (j<- 0 to i) {
      executeLine(s"insert 'key$i' valuevaluevaluevaluevalue$i")
    }
  }

  def find_test(key: String, i: Int): Unit = {
    println(s"finding from ${key}1 to $key$i...")
    val before=System.currentTimeMillis()
    for (j<-1 to i)
      executeLine(s"find '$key$i'")
    val after=System.currentTimeMillis()
    formatTime(after-before)
  }

  def insert_test(key: String, value: String, c: Int) = {
    println("inserting " + c + " key -> value...")
    val before: Long = System.currentTimeMillis
    for (i <- 1 to c)
      executeLine("insert '" + key + i + "' " + value)
    val after: Long = System.currentTimeMillis
    formatTime(after-before)
  }
}
