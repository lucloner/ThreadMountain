package net.vicp.biggee.kotlin.thread

import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

//此为包装后的线程池,主要功能为根据优先级顺序执行,等级越高执行越是排后
class ThreadMountain<T>(
    private val mountainName: String = UUID.randomUUID().toString(),
    private val daemon: Boolean = false
) : LinkedList<Pair<Callable<T>, Int>>(), Thread.UncaughtExceptionHandler {
    private val guardian = Executors.newSingleThreadScheduledExecutor()

    //运行时集合
    private val workList = ArrayList<Pair<Int, Int>>()
    private val threadList = ArrayList<Thread>()
    private val deadList = Stack<Thread>()
    private val checkList = LinkedList<Pair<Int, Int>>()

    //返回集合
    val futures = HashMap<Int, T?>()
    val exceptions = HashMap<Int, Throwable>()

    init {
        guardian.scheduleAtFixedRate({
            val work = this.poll() ?: return@scheduleAtFixedRate
            var levelOK = true
            val deadIndexes = ArrayList<Int>()

            for (index in 0 until threadList.size) {
                if (!threadList[index].isAlive) {
                    deadIndexes.add(index)
                }
            }
            deadIndexes.iterator().forEach {
                workList.removeAt(it)
                threadList.removeAt(it)
            }

            workList.iterator().forEach {
                if (it.second < work.second) {
                    levelOK = false
                    return@forEach
                }
            }
            if (levelOK) {
                val callableHashCode = work.first.hashCode()
                workList.add(Pair(callableHashCode, work.second))
                threadList.add(
                    Thread {
                        futures[callableHashCode] = work.first.call()
                        deadList.push(Thread.currentThread())
                    }.apply {
                        uncaughtExceptionHandler = this@ThreadMountain
                        isDaemon = daemon
                        start()
                    }
                )
            } else {
                offer(work)
            }

            while (deadList.isNotEmpty()) {
                try {
                    val thread = deadList.pop()
                    if (thread.isAlive) {
                        thread.stop()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, 1, 1, TimeUnit.MILLISECONDS)

        guardian.scheduleAtFixedRate({
            if (workList.isNotEmpty()) {
                System.gc()
            }

            while (checkList.isNotEmpty()) {
                val workToCheck = checkList.pop()
                val thread = threadList[workList.indexOf(workToCheck)]
                if (futures.containsKey(workToCheck.first) && !thread.isAlive) {
                    deadList.push(thread)
                }
            }

            checkList.addAll(workList)
        }, 1, 1, TimeUnit.MINUTES)
    }

    /**
     * Method invoked when the given thread terminates due to the
     * given uncaught exception.
     *
     * Any exception thrown by this method will be ignored by the
     * Java Virtual Machine.
     * @param t the thread
     * @param e the exception
     */
    override fun uncaughtException(t: Thread?, e: Throwable?) {
        t ?: return
        e ?: return
        e.printStackTrace()
        val index = threadList.indexOf(t)
        val work = workList.get(index)
        exceptions[work.first] = e
    }
}

fun main(args: Array<String>) {
    val c1 = object : Callable<Any> {
        override fun call(): Any {
            System.out.println("\n!START(${this.hashCode()})!!!!!!!!!!!!!!!!!!!!!!!")
            for (i in 1..10) {
                System.out.print("!$i")
                Thread.sleep(100)
            }
            System.out.println("\n!END!!!!!!!!!!!!!!!!!!!!!!!")
            return 1
        }
    }.also {
        System.out.println("callable c1 hash:${it.hashCode()}")
    }
    val c2 = object : Callable<Any> {
        override fun call(): Any {
            System.out.println("\n@START!(${this.hashCode()})@@@@@@@@@@@@@@@@@@@@@@")
            for (i in 1..10) {
                System.out.print("@$i")
                Thread.sleep(100)
            }
            System.out.println("\n@END@@@@@@@@@@@@@@@@@@@@@@@")
            return 2
        }
    }.also {
        System.out.println("callable c2 hash:${it.hashCode()}")
    }
    val c3 = object : Callable<Any> {
        override fun call(): Any {
            System.out.println("\n#START(${this.hashCode()})#######################")
            for (i in 1..10) {
                System.out.print("#$i")
                Thread.sleep(100)
            }
            System.out.println("\n#END#######################")
            return 3
        }
    }.also {
        System.out.println("callable c3 hash:${it.hashCode()}")
    }
    val c4 = object : Callable<Any> {
        override fun call(): Any {
            System.out.println("\n%START(${this.hashCode()})%%%%%%%%%%%%%%%%%%%%%%%")
            for (i in 1..10) {
                System.out.print("%$i")
                Thread.sleep(100)
            }
            System.out.println("\n%END%%%%%%%%%%%%%%%%%%%%%%%")
            return 4
        }
    }.also {
        System.out.println("callable c4 hash:${it.hashCode()}")
    }

    val m = ThreadMountain<Any>()
    m.offer(Pair(c1, 1))
    m.offer(Pair(c2, 2))
    m.offer(Pair(c3, 2))
    m.offer(Pair(c4, 3))
}