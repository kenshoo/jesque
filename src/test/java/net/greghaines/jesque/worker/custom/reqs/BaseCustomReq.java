package net.greghaines.jesque.worker.custom.reqs;

import net.greghaines.jesque.Config;
import net.greghaines.jesque.ConfigBuilder;
import net.greghaines.jesque.Job;
import net.greghaines.jesque.client.Client;
import net.greghaines.jesque.client.ClientImpl;
import net.greghaines.jesque.worker.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.greghaines.jesque.utils.JesqueUtils.entry;
import static net.greghaines.jesque.utils.JesqueUtils.map;


public class BaseCustomReq {

    final Config config = new ConfigBuilder().withHost("localhost").withPort(6379).build();
    final String LOW_PRIORITY_QUEUE = "lowPriorityQueue";
    final String HIGH_PRIORITY_QUEUE = "highPriorityQueue";
    private Watchdog watchdog = null;

    private static void join(Thread workerThread) {
        try {
            workerThread.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    int getNumberOFWorkers() {
        return 17;
    }

    //@Before
    public void resetRedis() {
        /*TestUtils.resetRedis(config);*/
    }

    Map<Worker, Thread> startWorkers(String clazName, Class<?> claz) {
        Map<Worker, Thread> workers = new HashMap<>(getNumberOFWorkers());

        for (int i = 0; i < getNumberOFWorkers(); i++) {
            final Worker worker = new WorkerImpl(config,
                    Arrays.asList(HIGH_PRIORITY_QUEUE, LOW_PRIORITY_QUEUE),
                    new MapBasedJobFactory(map(entry(clazName, claz))), new JedisFactory(config).createJedis(),
                    NextQueueStrategy.RESET_TO_HIGHEST_PRIORITY);


//            worker.getWorkerEventEmitter().addListener(new CustomWorkerListener(), WorkerEvent.JOB_PROCESS);
//            worker.getWorkerEventEmitter().addListener(new CustomWorkerListener(), WorkerEvent.JOB_SUCCESS);
//            worker.getWorkerEventEmitter().addListener(new CustomWorkerListener(), WorkerEvent.JOB_FAILURE);

            final Thread workerThread = new Thread(worker);
            workerThread.start();
            workers.put(worker, workerThread);
        }

        switchOnWatchdog();

        return workers;
    }

    private void switchOnWatchdog() {
        watchdog = new Watchdog(config, new JedisFactory(config));

        watchdog.switchOn();
    }


    void delayEnqueueJobs(final String queue, final Map<Job, Long> jobs, final Config config) {
        delayEnqueueJobs(queue, jobs, System.currentTimeMillis(), config);
    }

    void delayEnqueueJobs(final String queue, final Map<Job, Long> jobs, long start, final Config config) {
        final Client client = new ClientImpl(config);
        try {
            jobs.forEach((job, time) -> {
                final long future = start + time;
                client.delayedEnqueue(queue, job, future);
            });

        } finally {
            client.end();
        }
    }

    void enqueueJobs(final String queue, final Set<Job> jobs, long start, final Config config) {
        final Client client = new ClientImpl(config);
        try {
            jobs.forEach((job) -> {
                client.enqueue(queue, job);
            });

        } finally {
            client.end();
        }
    }

    Long getRandom(long minimum, long maximum) {
        return minimum + (long) (Math.random() * maximum);
    }

    void stopWorkers(final Map<Worker, Thread> workers) {
        workers.forEach((worker, workerThread) -> {
            worker.end(true);
            join(workerThread);
        });

        if (watchdog != null) {
            watchdog.switchOff();
        }
    }
}
