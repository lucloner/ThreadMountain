package net.vicp.biggee.java.thread;

import com.sun.istack.internal.NotNull;

import java.util.*;
import java.util.concurrent.*;

public final class ThreadMountain<T> extends LinkedList<ThreadMountain.Work> {
    public final String mountainName;
    public final boolean daemon;
    public final long timeout;
    public final ThreadGroup threadGroup;
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private final ThreadFactory threadFactory;
    private final ScheduledExecutorService taskManager = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService guardian = Executors.newSingleThreadScheduledExecutor();

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

        uncaughtExceptionHandler = (t, e) -> {
            final Iterator<ThreadMountain.Work> workListIterator = workList.iterator();
            while (workListIterator.hasNext()) {
                final ThreadMountain.Work it = workListIterator.next();
                if (it.sameThread(t)) {
                    exceptions.put(it.hashCode(), e);
                    return;
                }
            }
        };

        threadGroup = new ThreadGroup(mountainName);
        threadGroup.setDaemon(this.daemon);
        threadFactory = (r) -> {
            final Thread thread = new Thread(threadGroup, r, this.mountainName + "_" + r.hashCode());
            thread.setDaemon(this.daemon);
            thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            return thread;
        };

        //开始做事
        taskManager.scheduleAtFixedRate(() -> {
            //排序
            this.sort(Comparator.comparingInt(o -> o.fakeLevel));

            //声明
            final ThreadMountain.Work work = poll();
            if (work == null) {
                return;
            }

            final ArrayList<ThreadMountain.Work> done = new ArrayList<>();
            int minlevel = Integer.MAX_VALUE;
            final Iterator<ThreadMountain.Work> workListIterator = workList.iterator();
            while (workListIterator.hasNext()) {
                final ThreadMountain.Work it = workListIterator.next();
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

        guardian.scheduleAtFixedRate(() -> {
            //timeout防止内存泄露(单位:秒)

            //对于运行超过2 x timeout的线程检查
            final Iterator<ThreadMountain.Work> checkListIterator = checkList.iterator();
            while (checkListIterator.hasNext()) {
                final ThreadMountain.Work it = checkListIterator.next();
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
            while (workListIterator.hasNext()) {
                final ThreadMountain.Work it = workListIterator.next();
                if (it.executed() && it.isAlive() && futures.get(it.hashCode()).isDone()) {
                    checkList.add(it);
                }
            }

        }, this.timeout, this.timeout, TimeUnit.MILLISECONDS);
    }

    public final boolean addWork(@NotNull Callable<? extends T> callable, int level) {
        return offer(new ThreadMountain.Work(callable, level));
    }

    protected final class Work extends Object implements Callable<T> {
        private final ExecutorService pool = Executors.newSingleThreadExecutor(threadFactory);
        private final Callable<? extends T> callable;
        volatile int fakeLevel;
        private volatile Thread thread = null;

        //构造函数
        Work(@NotNull final Callable<? extends T> callable, final int level) {
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

        final boolean executed() {
            return thread != null;
        }

        final boolean isAlive() {
            return thread.isAlive();
        }

        final Callable<? extends T> callable() {
            return callable;
        }

        final int level() {
            return fakeLevel;
        }

        final boolean sameThread(Thread thread) {
            return this.thread.equals(thread);
        }

        final Future<? extends T> doSubmit() {
            return pool.submit(this);
        }

        final void shutdown() {
            pool.shutdown();
        }

        final void stop() {
            pool.shutdownNow();
        }

        final void dispose() throws Throwable {
            finalize();
        }
    }
}
