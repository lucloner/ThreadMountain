package net.vicp.biggee.kotlin.thread

import java.util.*
import java.util.concurrent.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

//此为包装后的线程池,主要功能为根据优先级顺序执行,等级越高执行越是排后
class ThreadMountain<T>(private val mountainName: String = UUID.randomUUID().toString(), private val daemon: Boolean = false) :LinkedBlockingQueue<Pair<Callable<T>,Int>>(), ThreadFactory, Thread.UncaughtExceptionHandler {
    private val pool = Executors.newCachedThreadPool(this)
    private val guardian = Executors.newScheduledThreadPool(1, this)
    val threadGroup = ThreadGroup(mountainName).apply {
        isDaemon = daemon
    }

    //运行时集合
    private val workList = ArrayList<Pair<Int, Int>>()
    private val threadList = ArrayList<Thread>()

    //返回集合
    val futures = HashMap<Int, Future<T?>>()
    val exceptions = HashMap<Int, Throwable>()

    init {
        guardian.scheduleAtFixedRate({
            var deadIndexes = ArrayList<Int>()
            for (index in 0 until threadList.size) {
                if (!threadList[index].isAlive) {
                    deadIndexes.add(index)
                }
            }
            deadIndexes.iterator().forEach {
                workList.removeAt(it)
                threadList.removeAt(it)
            }

            val work = this.poll() ?: return@scheduleAtFixedRate
            var levelOK = true
            workList.iterator().forEach {
                if (it.second < work.second) {
                    levelOK = false
                    return@forEach
                }
            }
            if (levelOK) {
                val callableHashCode = work.first.hashCode()
                workList.add(Pair(callableHashCode, work.second))
                val future = pool.submit(work.first)
                futures[callableHashCode] = future
            } else {
                offer(work)
            }

        }, 1, 1, TimeUnit.MICROSECONDS)
    }

    /**
     * Constructs a new `Thread`.  Implementations may also initialize
     * priority, name, daemon status, `ThreadGroup`, etc.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread, or `null` if the request to
     * create a thread is rejected
     */
    override fun newThread(r: Runnable?): Thread {
        val thread = Thread(threadGroup, r).apply {
            name = "${mountainName}_${System.currentTimeMillis()}"
            uncaughtExceptionHandler = this@ThreadMountain
        }
        threadList.add(thread)
        return thread
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
        val index=threadList.indexOf(t)
        val work=workList.get(index)
        exceptions[work.first]=e
    }
}