package net.vicp.biggee.kotlin.thread

import sun.misc.ThreadGroupUtils
import java.util.*
import java.util.concurrent.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

class ThreadMountain<T>(val mountainName: String = UUID.randomUUID().toString()) : ThreadFactory {
    private val threadGroup = ThreadGroup("${mountainName}_group").also {
        
    }
    private val pool=ArrayList<Thread>()
    val futures = HashMap<Callable<T?>, T?>()
    private val runlevelList = LinkedHashMap<Callable<T?>, Int>()
    private val threadMap = HashMap<Callable<T?>, Thread>()
    val exceptions = HashMap<Any, Throwable>()

    /**
     * Constructs a new `Thread`.  Implementations may also initialize
     * priority, name, daemon status, `ThreadGroup`, etc.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread, or `null` if the request to
     * create a thread is rejected
     */
    override fun newThread(r: Runnable?): Thread {
        val thread = Thread(r).apply {
            isDaemon = false
            name = "${mountainName}_${System.currentTimeMillis()}"
        }
        return thread
    }

    fun add(callable: Callable<T?>, runlevel: Int): ThreadMountain<T> {
        System.out.println("包装执行,主控名称:${mountainName}\t${callable.hashCode()}")
        val newCallable = Callable {
            val thread = Thread.currentThread()
            System.out.println("现在开始执行:${thread.name}\t${callable.hashCode()}")
            threadMap[callable] = thread
            runlevelList.iterator().forEach {
                if (it.value < runlevel && threadMap.contains(it.key)) {
                    System.out.println("优先执行")
                    val lowerThread = threadMap[it.key]
                    lowerThread?.join()
                    System.out.println("优先执行完毕")
                }
            }
            var result: T? = null
            try {
                result = callable.call()
            } catch (e: Exception) {
                e.printStackTrace()
                exceptions[callable] = e
            }
            threadMap.remove(callable)
            System.out.println("现在执行完毕:${thread.name}\t${callable.hashCode()}")
            return@Callable result
        }
        runlevelList[callable] = runlevel
        val future = pool.submit(newCallable)
        Thread { futures.put(callable, future.get()) }.start()
        return this
    }

    fun add(runnable: Runnable, runlevel: Int): ThreadMountain<T> {
        val callable = Callable<T?> {
            try {
                runnable.run()
            } catch (e: Exception) {
                exceptions[runnable] = e
            }
            return@Callable null
        }
        return add(callable, runlevel)
    }
}

fun main(args: Array<String>) {
    val p= Executors.newCachedThreadPool {
        println("in factory:${it.hashCode()}")
        return@newCachedThreadPool Thread(it)
    }

    val r= Runnable {
        println("just runnable")
    }

    val t=Thread(r)

    println("orig runnable:${r.hashCode()}")

    p.execute(r)
    t.start()

    println("thread runnable:${r.hashCode()}")
}