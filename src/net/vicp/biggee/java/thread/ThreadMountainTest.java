package net.vicp.biggee.java.thread;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ThreadMountainTest {

    public static void main(String[] args) {
        new ThreadMountainTest().test();
    }

    public void test() {
        final ThreadMountain<Object> m = new ThreadMountain<>();
        final Callable<Object> c1 = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                System.out.println("\n!START(" + this.hashCode() + ")!!!!!!!!!!!!!!!!!!!!!!!");
                for (int i = 0; i < 11; i++) {
                    System.out.print("!" + i);
                    Thread.sleep(100);
                }
                System.out.println("\n!END!!!!!!!!!!!!!!!!!!!!!!!");
                return 1;
            }
        };
        final Callable<Object> c2 = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                System.out.println("\n@START(" + this.hashCode() + ")@@@@@@@@@@@@@@@@@@@@@@@");
                for (int i = 0; i < 11; i++) {
                    System.out.print("@" + i);
                    Thread.sleep(100);
                }
                System.out.println("\n@END@@@@@@@@@@@@@@@@@@@@@@@");
                return 1;
            }
        };
        final Callable<Object> c3 = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                System.out.println("\n#START(" + this.hashCode() + ")#######################");
                for (int i = 0; i < 11; i++) {
                    System.out.print("#" + i);
                    Thread.sleep(100);
                }
                System.out.println("\n#END#######################");
                return 1;
            }
        };
        final Callable<Object> c4 = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                System.out.println("\n%START(" + this.hashCode() + ")%%%%%%%%%%%%%%%%%%%%%%%");
                for (int i = 0; i < 11; i++) {
                    System.out.print("%" + i);
                    Thread.sleep(100);
                }
                System.out.println("\n%END%%%%%%%%%%%%%%%%%%%%%%%");
                return 1;
            }
        };
        final Callable<Object> cEnd = () -> {
            System.out.println();
            final ThreadGroup tg = Thread.currentThread().getThreadGroup();
            final int total = tg.activeCount();
            final Thread[] ts = new Thread[total];
            tg.enumerate(ts);
            for (int i = 0; i < total; i++) {
                final Thread it = ts[i];
                System.out.println("===Thread:" + it.getName() + "," +
                        "Alive:" + it.isAlive() + "," +
                        "State:" + it.getState() + "," +
                        "Trace:" + Arrays.toString(it.getStackTrace()));
            }

            final Iterator<Callable<Object>> returnCodeIterator = m.returnCode.keySet().iterator();
            while (returnCodeIterator.hasNext()) {
                final Callable<Object> callable = returnCodeIterator.next();
                final int rCode = callable.hashCode();
                final Iterator<Integer> futuresIterator = m.futures.keySet().iterator();
                while (futuresIterator.hasNext()) {
                    final int it = futuresIterator.next();
                    System.out.println("===callable:" + rCode + "," +
                            "return:" + m.futures.get(it).get(m.timeout, TimeUnit.MILLISECONDS) + "," +
                            "exception:" + m.exceptions.get(it));
                }
            }
            return Integer.MAX_VALUE;
        };

        System.out.println("add c1");
        m.addWork(c1, 3);
        System.out.println("add c2");
        m.addWork(c2, 2);
        System.out.println("add c3");
        m.addWork(c3, 1);
        System.out.println("add c4");
        m.addWork(c4, 2);
        System.out.println("add cEnd");
        m.addWork(cEnd, Integer.MAX_VALUE);
        System.out.println("main end");
    }
}
