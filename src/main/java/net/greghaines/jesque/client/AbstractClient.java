/*
 * Copyright 2011 Greg Haines
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.greghaines.jesque.client;

import static net.greghaines.jesque.utils.ResqueConstants.QUEUE;
import static net.greghaines.jesque.utils.ResqueConstants.QUEUES;

import net.greghaines.jesque.Config;
import net.greghaines.jesque.DuplicateJobException;
import net.greghaines.jesque.Job;
import net.greghaines.jesque.json.ObjectMapperFactory;
import net.greghaines.jesque.utils.JedisUtils;
import net.greghaines.jesque.utils.JesqueUtils;
import net.greghaines.jesque.utils.ScriptUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common logic for Client implementations.
 *
 * @author Greg Haines
 * @author Animesh Kumar
 */
public abstract class AbstractClient implements Client {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractClient.class);

    private static final String ENQUEUE = "enqueue";
    private static final String PRIORITY_ENQUEUE = "priorityEnqueue";
    private static final String DELAYED_ENQUEUE = "delayedEnqueue";
    private static final String DUPLICATED = "duplicated";
    private final String namespace;
    protected boolean jobUniquenessValidation;
    private static final String PUSH_LUA = "/clientScripts/jesque_push.lua";
    private static final AtomicReference<String> pushScriptHash = new AtomicReference<>(null);

    /**
     * Constructor.
     *
     * @param config
     *            used to get the namespace for key creation
     */
    protected AbstractClient(final Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.namespace = config.getNamespace();
    }

    /**
     * @return the namespace this client will use
     */
    protected String getNamespace() {
        return this.namespace;
    }

    protected boolean getJobUniquenessValidation() {
        return jobUniquenessValidation;
    }

    private static boolean isScriptLoaded = false;

    static void loadScript(Jedis jedis) {
        if(!isScriptLoaded) {
            synchronized (AbstractClient.class) {
                if(!isScriptLoaded) {
                    doLoadScript(jedis);
                    isScriptLoaded = true;
                }
            }
        }
    }

    private static void doLoadScript(final Jedis jedis) {
        try {
            pushScriptHash.set(jedis.scriptLoad(ScriptUtils.readScript(PUSH_LUA)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds a namespaced Redis key with the given arguments.
     *
     * @param parts
     *            the key parts to be joined
     * @return an assembled String key
     */
    protected String key(final String... parts) {
        return JesqueUtils.createKey(this.namespace, parts);
    }

    abstract Jedis getJedis();

    /**
     * {@inheritDoc}
     */
    @Override
    public void enqueue(final String queue, final Job job) {
        validateArguments(queue, job);
        try {
            doEnqueue(queue, ObjectMapperFactory.get().writeValueAsString(job));
        } catch (JedisException re) {
            doLoadScript(getJedis());
            throw re;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void priorityEnqueue(final String queue, final Job job) {
        validateArguments(queue, job);
        try {
            doPriorityEnqueue(queue, ObjectMapperFactory.get().writeValueAsString(job));
        } catch (JedisException re) {
            doLoadScript(getJedis());
            throw re;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acquireLock(final String lockName, final String lockHolder, final int timeout) {
        if ((lockName == null) || "".equals(lockName)) {
            throw new IllegalArgumentException("lockName must not be null or empty: " + lockName);
        }
        if ((lockHolder == null) || "".equals(lockHolder)) {
            throw new IllegalArgumentException("lockHolder must not be null or empty: " + lockHolder);
        }
        if (timeout < 1) {
            throw new IllegalArgumentException("timeout must be a positive number");
        }
        try {
            return doAcquireLock(lockName, lockHolder, timeout);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Actually enqueue the serialized job.
     *
     * @param queue
     *            the queue to add the Job to
     * @param msg
     *            the serialized Job
     * @throws Exception
     *             in case something goes wrong
     */
    protected abstract void doEnqueue(String queue, String msg) throws Exception;

    /**
     * Actually enqueue the serialized job with high priority.
     *
     * @param queue
     *            the queue to add the Job to
     * @param msg
     *            the serialized Job
     * @throws Exception
     *             in case something goes wrong
     */
    protected abstract void doPriorityEnqueue(String queue, String msg) throws Exception;

    /**
     * Actually acquire the lock based upon the client acquisition model.
     *
     * @param lockName
     *            the name of the lock to acquire
     * @param timeout
     *            number of seconds until the lock will expire
     * @param lockHolder
     *            a unique string identifying the caller
     * @return true, if the lock was acquired, false otherwise
     * @throws Exception
     *             in case something goes wrong
     */
    protected abstract boolean doAcquireLock(final String lockName, final String lockHolder,
            final int timeout) throws Exception;

    /**
     * Helper method that encapsulates the minimum logic for adding a job to a
     * queue.
     *
     * @param jedis
     *            the connection to Redis
     * @param namespace
     *            the Resque namespace
     * @param queue
     *            the Resque queue name
     * @param jobJson
     *            the job serialized as JSON
     */
    public static void doEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson) {
        doEnqueue(jedis,namespace,queue, jobJson , false);
    }

    static void doEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson, boolean jobUniquenessValidation) {
        final String queuesKey = JesqueUtils.createKey(namespace, QUEUES);
        final String queueKey = JesqueUtils.createKey(namespace, QUEUE, queue);

        doEnqueue(jedis, ENQUEUE, queue, jobJson, queueKey, queuesKey, jobUniquenessValidation);
    }

    /**
     * Helper method that encapsulates the minimum logic for adding a high
     * priority job to a queue.
     *
     * @param jedis
     *            the connection to Redis
     * @param namespace
     *            the Resque namespace
     * @param queue
     *            the Resque queue name
     * @param jobJson
     *            the job serialized as JSON
     */
    public static void doPriorityEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson) {
        doPriorityEnqueue(jedis,namespace, queue, jobJson,false);
    }

    static void doPriorityEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson , boolean jobUniquenessValidation) {
        final String queuesKey = JesqueUtils.createKey(namespace, QUEUES);
        final String queueKey = JesqueUtils.createKey(namespace, QUEUE, queue);

        doEnqueue(jedis, PRIORITY_ENQUEUE, queue, jobJson, queueKey, queuesKey, jobUniquenessValidation);
    }

    /**
     * Helper method that encapsulates the logic to acquire a lock.
     *
     * @param jedis
     *            the connection to Redis
     * @param namespace
     *            the Resque namespace
     * @param lockName
     *            all calls to this method will contend for a unique lock with
     *            the name of lockName
     * @param timeout
     *            seconds until the lock will expire
     * @param lockHolder
     *            a unique string used to tell if you are the current holder of
     *            a lock for both acquisition, and extension
     * @return Whether or not the lock was acquired.
     */
    public static boolean doAcquireLock(final Jedis jedis, final String namespace, final String lockName, final String lockHolder, final int timeout) {
        final String key = JesqueUtils.createKey(namespace, lockName);
        // If lock already exists, extend it
        String existingLockHolder = jedis.get(key);
        if ((existingLockHolder != null) && existingLockHolder.equals(lockHolder)) {
            if (jedis.expire(key, timeout) == 1) {
                existingLockHolder = jedis.get(key);
                if ((existingLockHolder != null) && existingLockHolder.equals(lockHolder)) {
                    return true;
                }
            }
        }
        // Check to see if the key exists and is expired for cleanup purposes
        if (jedis.exists(key) && (jedis.ttl(key) < 0)) {
            // It is expired, but it may be in the process of being created, so
            // sleep and check again
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
            } // Ignore interruptions
            if (jedis.ttl(key) < 0) {
                existingLockHolder = jedis.get(key);
                // If it is our lock mark the time to live
                if ((existingLockHolder != null) && existingLockHolder.equals(lockHolder)) {
                    if (jedis.expire(key, timeout) == 1) {
                        existingLockHolder = jedis.get(key);
                        if ((existingLockHolder != null) && existingLockHolder.equals(lockHolder)) {
                            return true;
                        }
                    }
                } else { // The key is expired, whack it!
                    jedis.del(key);
                }
            } else { // Someone else locked it while we were sleeping
                return false;
            }
        }
        // Ignore the cleanup steps above, start with no assumptions test
        // creating the key
        if (jedis.setnx(key, lockHolder) == 1) {
            // Created the lock, now set the expiration
            if (jedis.expire(key, timeout) == 1) { // Set the timeout
                existingLockHolder = jedis.get(key);
                if ((existingLockHolder != null) && existingLockHolder.equals(lockHolder)) {
                    return true;
                }
            } else { // Don't know why it failed, but for now just report failed
                     // acquisition
                return false;
            }
        }
        // Failed to create the lock
        return false;
    }

    public static void doDelayedEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson, final long future) {
        doDelayedEnqueue(jedis,namespace,queue,jobJson,future, false);
    }

    static void doDelayedEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson, final long future, boolean jobUniquenessValidation) {
        final String queueKey = JesqueUtils.createKey(namespace, QUEUE, queue);
        // Add task only if this queue is either delayed or unused
        if (JedisUtils.canUseAsDelayedQueue(jedis, queueKey)) {
            final String queuesKey = JesqueUtils.createKey(namespace, QUEUES);
            doEnqueue(jedis, DELAYED_ENQUEUE, queue, jobJson, queueKey, queuesKey, jobUniquenessValidation, future);
        } else {
            throw new IllegalArgumentException(queue + " cannot be used as a delayed queue");
        }
    }

    private static void doEnqueue(final Jedis jedis, final String enqueueType, final String queue, final String jobJson, final String queueKey, final String queuesKey, final boolean jobUniquenessValidation) {
        doEnqueue(jedis, enqueueType, queue, jobJson, queueKey, queuesKey, jobUniquenessValidation, 0L);
    }

    private static void doEnqueue(final Jedis jedis, final String enqueueType, final String queue, final String jobJson, final String queueKey, final String queuesKey, final boolean jobUniquenessValidation, final long future) {
        final String pushStatus;
        LOG.info("Jesque starting {} job {} to the queue {} with delay {} ", enqueueType, jobJson, queue, future);
        try {
            final String uniquenessValidation = String.valueOf(jobUniquenessValidation);

            if(future==0) {
                pushStatus = (String) jedis.evalsha(pushScriptHash.get(), 2, queuesKey, queueKey, enqueueType, getCurrTime(), queue, jobJson, uniquenessValidation);
            } else{
                pushStatus = (String) jedis.evalsha(pushScriptHash.get(), 2, queuesKey, queueKey, enqueueType, getCurrTime(), queue, jobJson, uniquenessValidation, String.valueOf(future));
            }
        } catch (Exception e) {
            LOG.error("Jesque {} job {} to the queue {} has failed", enqueueType, jobJson, queue, e);
            throw e;
        }

        if(DUPLICATED.equals(pushStatus)){
            final String duplicatedJobMessage = String.format("Jesque duplicated job %s has been found", jobJson);
            LOG.warn(duplicatedJobMessage,jobJson);
            throw new DuplicateJobException(duplicatedJobMessage);
        }

        LOG.info("Jesque {} job {} to the queue {} has finished successfully", enqueueType, jobJson, queue);
    }

    protected abstract void doDelayedEnqueue(String queue, String msg, long future) throws Exception;

    /**
     * {@inheritDoc}
     */
    @Override
    public void delayedEnqueue(final String queue, final Job job, final long future) {
        validateArguments(queue, job, future);
        try {
            doDelayedEnqueue(queue, ObjectMapperFactory.get().writeValueAsString(job), future);
        } catch (JedisException re) {
            doLoadScript(getJedis());
            throw re;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void doRemoveDelayedEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson) {
        final String key = JesqueUtils.createKey(namespace, QUEUE, queue);
        // remove task only if this queue is either delayed or unused
        if (JedisUtils.canUseAsDelayedQueue(jedis, key)) {
            jedis.zrem(key, jobJson);
        } else {
            throw new IllegalArgumentException(queue + " cannot be used as a delayed queue");
        }
    }

    protected abstract void doRemoveDelayedEnqueue(String queue, String msg) throws Exception;

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeDelayedEnqueue(final String queue, final Job job) {
        validateArguments(queue, job);
        try {
            doRemoveDelayedEnqueue(queue, ObjectMapperFactory.get().writeValueAsString(job));
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void doRecurringEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson, final long future, final long frequency){
        final String queueKey = JesqueUtils.createKey(namespace, QUEUE, queue);
        final String hashKey = JesqueUtils.createRecurringHashKey(queueKey);

        if (JedisUtils.canUseAsRecurringQueue(jedis, queueKey, hashKey)) {
            Transaction transaction = jedis.multi();
            transaction.zadd(queueKey, future, jobJson);
            transaction.hset(hashKey, jobJson, String.valueOf(frequency));
            if (transaction.exec() == null) {
                throw new RuntimeException("cannot add " + jobJson + " to recurring queue " + queue);
            }
        } else {
            throw new IllegalArgumentException(queue + " cannot be used as a recurring queue");
        }
    }

    protected abstract void doRecurringEnqueue(String queue, String msg, long future, long frequency) throws Exception;

    /**
     * {@inheritDoc}
     */
    @Override
    public void recurringEnqueue(String queue, Job job, long future, long frequency) {
        validateArguments(queue, job, future, frequency);
        try {
            doRecurringEnqueue(queue, ObjectMapperFactory.get().writeValueAsString(job), future, frequency);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void doRemoveRecurringEnqueue(final Jedis jedis, final String namespace, final String queue, final String jobJson) {
        final String queueKey = JesqueUtils.createKey(namespace, QUEUE, queue);
        final String hashKey = JesqueUtils.createRecurringHashKey(queueKey);

        if (JedisUtils.canUseAsRecurringQueue(jedis, queueKey, hashKey)) {
            Transaction transaction = jedis.multi();
            transaction.hdel(hashKey, jobJson);
            transaction.zrem(queueKey, jobJson);
            if (transaction.exec() == null) {
                throw new RuntimeException("cannot remove " + jobJson + " from recurring queue " + queue);
            }
        } else {
            throw new IllegalArgumentException(queue + " cannot be used as a recurring queue");
        }
    }

    protected abstract void doRemoveRecurringEnqueue(String queue, String msg) throws Exception;

    @Override
    public void removeRecurringEnqueue(String queue, Job job) {
        validateArguments(queue, job);
        try {
            doRemoveRecurringEnqueue(queue, ObjectMapperFactory.get().writeValueAsString(job));
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void validateArguments(final String queue, final Job job) {
        if (queue == null || "".equals(queue)) {
            throw new IllegalArgumentException("queue must not be null or empty: " + queue);
        }
        if (job == null) {
            throw new IllegalArgumentException("job must not be null");
        }
        if (!job.isValid()) {
            throw new IllegalStateException("job is not valid: " + job);
        }
    }

    private static void validateArguments(final String queue, final Job job, final long future) {
        validateArguments(queue, job);
        if (System.currentTimeMillis() > future) {
            throw new IllegalArgumentException("future must be after current time");
        }
    }

    private static void validateArguments(final String queue, final Job job, final long future, final long frequency) {
        validateArguments(queue, job, future);
        if (frequency < 1) {
            throw new IllegalArgumentException("frequency must be greater than one second");
        }
    }

    private static String getCurrTime() {
        return String.valueOf(System.currentTimeMillis());
    }
}
