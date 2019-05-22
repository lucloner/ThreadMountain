package net.vicp.biggee.java.thread;

import com.sun.istack.internal.NotNull;

import java.util.*;
import java.util.concurrent.*;

public final class ThreadMountain<T> extends LinkedList<ThreadMountain.Work> implements Thread.UncaughtExceptionHandler, ThreadFactory {
    public final String mountainName;
    public final boolean daemon;
    public final long timeout;
    public final ThreadGroup threadGroup;
    //返回集合
    public final HashMap<Callable<T>, LinkedHashSet<Integer>> returnCode = new HashMap<>();
    public final HashMap<Integer, Future<T>> futures = new HashMap<>();
    public final HashMap<Integer, Throwable> exceptions = new HashMap<>();
    //运行时集合
    private final HashSet<ThreadMountain.Work> workList = new HashSet<>();
    private final HashSet<ThreadMountain.Work> checkList = new HashSet<>();

    //构造方法
    public ThreadMountain() {
        this(null, null, null);
    }

    public ThreadMountain(final String mountainName, final Boolean daemon, final Long timeout) {
        //初始化
        if (mountainName != null) {
            this.mountainName = mountainName;
        } else {
            this.mountainName = UUID.randomUUID().toString();
        }
        if (daemon != null) {
            this.daemon = daemon;
        } else {
            this.daemon = false;
        }
        if (timeout != null) {
            this.timeout = timeout;
        } else {
            this.timeout = 5000L;
        }
        threadGroup = new ThreadGroup(mountainName);
        threadGroup.setDaemon(this.daemon);

        //开始做事
        ScheduledExecutorService taskManager = Executors.newSingleThreadScheduledExecutor();
        taskManager.scheduleAtFixedRate(() -> {
            //排序
            this.sort(Comparator.comparingInt(o -> o.fakeLevel));

            //声明
            final ThreadMountain.Work work = poll();
            final ArrayList<ThreadMountain.Work> done = new ArrayList<>();
            int minlevel = Integer.MAX_VALUE;
            final Iterator<ThreadMountain.Work> workListIterator = workList.iterator();
            ThreadMountain.Work it;
            while ((it = workListIterator.next()) != null) {
                if (it.isAlive()) {
                    minlevel = Integer.min(minlevel, it.level());
                } else {
                    done.add(it);
                }
            }
            workList.removeAll(done);

            if (work.level() > minlevel) {
                offer(work);
            } else {
                futures.put(work.hashCode(), work.doSubmit());
                work.shutdown();
                LinkedHashSet<Integer> returnCodeList = returnCode.get(work.callable());
                if (returnCodeList == null) {
                    returnCodeList = new LinkedHashSet<>();
                }
                returnCodeList.add(work.hashCode());
                returnCode.put(work.callable(), returnCodeList);
            }
        }, 1, 1, TimeUnit.MILLISECONDS);

        ScheduledExecutorService guardian = Executors.newSingleThreadScheduledExecutor();
        guardian.scheduleAtFixedRate(() -> {
            //timeout防止内存泄露(单位:秒)

            //对于运行超过2 x timeout的线程检查
            final Iterator<ThreadMountain.Work> checkListIterator = checkList.iterator();
            ThreadMountain.Work it;
            while ((it = checkListIterator.next()) != null) {
                it.fakeLevel = Integer.MAX_VALUE;
                try {
                    it.dispose();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
            checkList.clear();

            //加入检查列表
            final Iterator<ThreadMountain.Work> workListIterator = workList.iterator();
            while ((it = workListIterator.next()) != null) {
                if (it.executed() && it.isAlive() && futures.get(it.hashCode()).isDone()) {
                    checkList.add(it);
                }
            }

        }, this.timeout, this.timeout, TimeUnit.MILLISECONDS);
    }

    public final void addWork(Callable callable, int level) {
        offer(new ThreadMountain.Work(callable, level));
    }

    /**
     * Method invoked when the given thread terminates due to the
     * given uncaught exception.
     * <p>Any exception thrown by this method will be ignored by the
     * Java Virtual Machine.
     *
     * @param t the thread
     * @param e the exception
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        final Iterator<ThreadMountain.Work> workListIterator = workList.iterator();
        ThreadMountain.Work it;
        while ((it = workListIterator.next()) != null) {
            if (it.sameThread(t)) {
                exceptions.put(it.hashCode(), e);
                return;
            }
        }
    }

    /**
     * Constructs a new {@code Thread}.  Implementations may also initialize
     * priority, name, daemon status, {@code ThreadGroup}, etc.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread, or {@code null} if the request to
     * create a thread is rejected
     */
    @Override
    public Thread newThread(Runnable r) {
        final Thread thread = new Thread(threadGroup, r, mountainName + "_" + r.hashCode());
        thread.setDaemon(daemon);
        return thread;
    }

    public final class Work<T> extends Object implements Callable<T> {
        private final ExecutorService pool = Executors.newSingleThreadExecutor(ThreadMountain.this);
        private final Callable<T> callable;
        int fakeLevel;
        private Thread thread = null;

        //构造函数
        public Work(@NotNull final Callable<T> callable, final int level) {
            super();
            this.callable = callable;
            fakeLevel = level;
        }

        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         * @throws Exception if unable to compute a result
         */
        @Override
        public T call() throws Exception {
            thread = Thread.currentThread();
            workList.add(this);
            return callable.call();
        }

        public final boolean executed() {
            return thread != null;
        }

        public final boolean isAlive() {
            return thread.isAlive();
        }

        public final Callable<T> callable() {
            return callable;
        }

        public final int level() {
            return fakeLevel;
        }

        public final boolean sameThread(Thread thread) {
            return this.thread.equals(thread);
        }

        public final Future<T> doSubmit() {
            return pool.submit(this);
        }

        public final void shutdown() {
            pool.shutdown();
        }

        public final void stop() {
            pool.shutdownNow();
        }

        public final void dispose() throws Throwable {
            finalize();
        }
    }
}
