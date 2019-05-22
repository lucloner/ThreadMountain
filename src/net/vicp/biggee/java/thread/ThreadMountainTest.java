package net.vicp.biggee.java.thread;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

//@RunWith(Arquillian.class)
public class ThreadMountainTest {
    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClass(ThreadMountain.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    public static void main(String[] args) {
        new ThreadMountainTest().test();
    }

    public void test() {
        final ThreadMountain<?> m = new ThreadMountain<>();
        final Callable<?> c1 = new Callable<Object>() {
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
        final Callable<?> c2 = new Callable<Object>() {
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
        final Callable<?> c3 = new Callable<Object>() {
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
        final Callable<?> c4 = new Callable<Object>() {
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
        final Callable<?> cEnd = () -> {
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

            final Iterator<? extends Callable<?>> returnCodeIterator = m.returnCode.keySet().iterator();
            Callable<?> callable;
            while ((callable = returnCodeIterator.next()) != null) {
                final int rCode = callable.hashCode();
                final Iterator<Integer> futuresIterator = m.futures.keySet().iterator();
                Integer it;
                while ((it = futuresIterator.next()) != null) {
                    System.out.println("===callable:" + rCode + "," +
                            "return:" + m.futures.get(it).get(m.timeout, TimeUnit.MILLISECONDS) + "," +
                            "exception:" + m.exceptions.get(it));
                }
            }
            return Integer.MAX_VALUE;
        };

        m.addWork(c1, 3);
        m.addWork(c2, 2);
        m.addWork(c3, 1);
        m.addWork(c4, 2);
        m.addWork(cEnd, Integer.MAX_VALUE);
    }

}
