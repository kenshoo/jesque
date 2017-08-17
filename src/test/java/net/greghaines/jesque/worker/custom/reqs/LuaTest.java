package net.greghaines.jesque.worker.custom.reqs;

import net.greghaines.jesque.Job;
import net.greghaines.jesque.worker.Worker;
import net.greghaines.jesque.worker.custom.actions.CustomPrintAction;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;


public class LuaTest extends BaseCustomReq {
    private static final int WAIT_FOR_WORKERS = 1000 * 20;
    private static final int JOBS_NUMBER = 1;

    @Test
    public void testCode() throws Exception {
        Long start = System.currentTimeMillis();

        Map<Worker, Thread> workers = startWorkers(CustomPrintAction.class.getSimpleName(), CustomPrintAction.class);

        // Enqueue the job before worker is created and started
        final Map<Job, Long> jobs = new HashMap<>(JOBS_NUMBER);

        for (int i = 0; i < JOBS_NUMBER; i++) {
            jobs.put(new Job("CustomPrintAction", i, 2.3, false, "reqs"), 8000L);
        }
        String LOW_PRIORITY_QUEUE = "lowPriorityQueue";
        enqueueJobs(LOW_PRIORITY_QUEUE, jobs.keySet(), start, config);


        try { // Wait a bit to ensure the workers had time to process the job
            Thread.sleep(WAIT_FOR_WORKERS);
        } finally { // Stop the worker
            stopWorkers(workers);
        }

    }


}

