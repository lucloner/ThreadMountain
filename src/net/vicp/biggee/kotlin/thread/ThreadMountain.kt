package net.vicp.biggee.kotlin.thread

import java.util.*
import java.util.concurrent.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet
import kotlin.math.min

//此为包装后的线程池,主要功能为根据优先级顺序执行,等级越高执行越是排后
class ThreadMountain<T>(
    val mountainName: String = UUID.randomUUID().toString(),
    val daemon: Boolean = false,
    timeout: Long = 5000
) : LinkedList<Pair<Callable<T>, Int>>(), Thread.UncaughtExceptionHandler, ThreadFactory {
    private val guardian = Executors.newScheduledThreadPool(1)
    private val taskManager = Executors.newScheduledThreadPool(1)
    val threadGroup = ThreadGroup(mountainName).apply {
        isDaemon = daemon
    }

    //运行时集合
    private val workList = HashSet<Work>()
    private val checkList = HashSet<Work>()

    //返回集合
    val returnCode = HashMap<Callable<T>, LinkedHashSet<Int>>()
    val futures = HashMap<Int, Future<T>?>()
    val exceptions = HashMap<Int, Throwable>()

    init {
        taskManager.scheduleAtFixedRate({
            //排序
            Collections.sort(this, kotlin.Comparator { o1, o2 ->
                return@Comparator o1.second - o2.second
            })

            //声明
            val work = Work(poll() ?: return@scheduleAtFixedRate)
            val done = ArrayList<Work>()
            var minlevel = Int.MAX_VALUE
            workList.iterator().forEach {
                if (it.isAlive()) {
                    minlevel = min(minlevel, it.level())
                } else {
                    done.add(it)
                }
            }
            workList.removeAll(done)

            if (work.level() > minlevel) {
                offer(work.queue)
            } else {
                futures[work.hashCode()] = work.doSubmit()
                work.shutdown()
                val returnCodeList = returnCode[work.callable()] ?: LinkedHashSet()
                returnCodeList.add(work.hashCode())
                returnCode[work.callable()] = returnCodeList
            }
        }, 1, 1, TimeUnit.MILLISECONDS)

        guardian.scheduleAtFixedRate({
            //timeout防止内存泄露(单位:秒)

            //对于运行超过2 x timeout的线程检查
            checkList.iterator().forEach {
                it.fakeLevel = Int.MAX_VALUE
                try {
                    it.dispose()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            checkList.clear()

            //加入检查列表
            workList.iterator().forEach {
                if (it.executed() && it.isAlive() && futures[it.hashCode()]?.isDone == true) {
                    checkList.add(it)
                }
            }
        }, timeout, timeout, TimeUnit.MILLISECONDS)
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
        return Thread(threadGroup, r).apply {
            name = "${mountainName}_${r.hashCode()}"
            uncaughtExceptionHandler = this@ThreadMountain
            isDaemon = daemon
        }
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
        workList.iterator().forEach {
            if (it.sameThread(t)) {
                exceptions[it.hashCode()] = e
                return
            }
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    internal inner class Work(val queue: Pair<Callable<T>, Int>) : java.lang.Object(), Callable<T> {
        private val thread by lazy { Thread.currentThread() }
        var fakeLevel = queue.second
        private val pool = Executors.newSingleThreadExecutor(this@ThreadMountain)

        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         * @throws Exception if unable to compute a result
         */
        override fun call(): T {
            thread
            workList.add(this)
            return queue.first.call()
        }

        fun executed() = thread != null
        fun isAlive() = thread?.isAlive ?: true
        fun callable() = queue.first
        fun level() = fakeLevel
        fun sameThread(thread: Thread) = this.thread?.equals(thread) ?: false
        fun doSubmit(): Future<T>? = pool.submit(this)
        fun shutdown() = pool.shutdown()
        fun stop() = pool.shutdownNow()
        fun dispose() = finalize()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val m = ThreadMountain<Any>(timeout = 1000)
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
            val cEnd = Callable<Any>
            {
                System.out.println()
                val tg = Thread.currentThread().threadGroup
                val total = tg.activeCount()
                val ts = Array(total) { Thread() }
                tg.enumerate(ts)
                ts.iterator().forEach {
                    System.out.println("===Thread:${it.name},Alive:${it.isAlive},State:${it.state}，Trace:${it.stackTrace.toList()}")
                }
                m.returnCode.iterator().forEach { rCode ->
                    rCode.value.iterator().forEach {
                        System.out.println(
                            "===callable:${rCode.key.hashCode()},return:${m.futures[it]?.get(
                                5,
                                TimeUnit.SECONDS
                            )},exception:${m.exceptions[it]}"
                        )
                    }
                }
            }

            m.offer(Pair(c1, 3))
            m.offer(Pair(c2, 2))
            m.offer(Pair(c3, 1))
            m.offer(Pair(c4, 2))
            m.offer(Pair(cEnd, Int.MAX_VALUE))
        }
    }
}
