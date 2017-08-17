package net.greghaines.jesque.worker;

import redis.clients.jedis.Jedis;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by dimav on 17/08/17.
 *
 */
class LightHouse {
    private static final int POOL_SIZE = 1;
    private static final boolean MAY_INTERRUPT_IF_RUNNING = false;
    private static final int INITIAL_DELAY = 0;
    private static final int LIGHTHOUSE_PERIOD = 2;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(POOL_SIZE);
    private final Jedis jedis;
    private final LightHouseUtils utils;
    private ScheduledFuture<?> lighthouseHandler;

    LightHouse(Jedis jedis, LightHouseUtils utils) {
        this.jedis = jedis;
        this.utils = utils;
    }

    void switchOn(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        transmitSignOfLife();
    }

    void switchOff() {
        if (lighthouseHandler != null) {
            lighthouseHandler.cancel(MAY_INTERRUPT_IF_RUNNING);
            lighthouseHandler = null;
        }
    }

    private void transmitSignOfLife() {
        final Runnable lighthouse = () -> {
            try {
                System.out.println("--------------- transmitSignOfLife");

                Long redisTime = utils.getRedisTime(jedis);

                String signOfLifeKey = utils.getSignOfLifeKey();

                System.out.println(signOfLifeKey + " " + new Date(redisTime));

                jedis.set(signOfLifeKey, String.valueOf(redisTime));
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        };

        lighthouseHandler = scheduler.scheduleAtFixedRate(lighthouse, INITIAL_DELAY, LIGHTHOUSE_PERIOD, SECONDS);

    }


}
