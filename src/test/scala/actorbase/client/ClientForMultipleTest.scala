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
  * Created by kurt on 02/06/2016.
  */
class ClientForMultipleTest {
  var connection: Connection = null
  var printOutput: Boolean = false

  def main(args: Array[String]): Unit = {
    Welcome.printWelcomeMessage
    // Readline loop
    print("> ")
    for (ln <- io.Source.stdin.getLines) {
      ln match {
        case "autoconn" => executeLine("connect localhost:8181 admin admin")
        case "output_off" => {
          println("standard output OFF")
          printOutput = false
        }
        case "output_on" => {
          println("standard output ON")
          printOutput = true
        }
        case "overload_test1" => executeOverloadTest1(25)
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
              println("inserting " + m.group(3) + " key -> value...")
              val before: Long = System.currentTimeMillis
              for (i <- 1 to m.group(3).toInt)
                executeLine("insert '" + m.group(1) + i + "' " + m.group(2))
              val after: Long = System.currentTimeMillis
              formatTime(after-before)
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
                executeLine(ln.trim)
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
        if (printOutput) println(out)
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
      connection = Driver.connect(regex.group(1), Integer.parseInt(regex.group(2)), regex.group(3), regex.group(4)).asInstanceOf[Connection]
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

  def executeOverloadTest1(K:Int): Unit = {
    val start: Long = System.currentTimeMillis
    //print ("creating a couple of maps...\t")
    val mapNames = new Array[String] (5)
    for (i<- 0 to 4) {
      val newMap = s"testMap${System.currentTimeMillis}$i"
      mapNames(i) = newMap
      executeLine(s"createmap $newMap")
    }

    val mapsCreated: Long = System.currentTimeMillis
    //formatTime(mapsCreated-start)

    //print (s"\nnow inserting $K key-value tuples in each map...\t")
    for (map<-mapNames) {
      executeLine(s"selectmap $map")
      for (i<-1 to K)
        executeLine(s"insert 'key$i' value$i")
    }

    val tuplesInserted: Long = System.currentTimeMillis
    //formatTime(tuplesInserted-mapsCreated)

    //print ("\nnow finding each value inserted...\t")
    for (map <- mapNames) {
      executeLine(s"selectmap $map")
      for (i<-1 to K)
        executeLine(s"find 'key$i'")
    }

    val keyFound: Long = System.currentTimeMillis
    //formatTime(keyFound-tuplesInserted)

    //print ("\nTotal time= ")
    formatTime(keyFound-start)

  }
}


object main {
  def main(args: Array[String]) {

    val tonsOfThreads = new Array[Thread](2000)

    for (i<-0 to tonsOfThreads.length-1)
      tonsOfThreads(i) = new Thread(new Runnable {
        override def run(): Unit = {
          val client=new ClientForMultipleTest
          client.executeLine("connect localhost:8181 admin admin")
          Thread.sleep(1000)
          client.executeLine("selectdb test")
          Thread.sleep(1000)
          client.executeOverloadTest1(10)
        }
      })


    println ("running 1 thread")
    tonsOfThreads(0).run()

    Thread.sleep(1000)

    println("\n\n\nrunning multiple threads")
    for (t <- tonsOfThreads) {
      t.start()
      //Thread.sleep(25)
    }

  }
}