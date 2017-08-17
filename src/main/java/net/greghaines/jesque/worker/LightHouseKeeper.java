package net.greghaines.jesque.worker;

import redis.clients.jedis.Jedis;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by dimav on 17/08/17.
 *
 */
class LightHouseKeeper {

    private static final int POOL_SIZE = 1;

    private static final boolean MAY_INTERRUPT_IF_RUNNING = false;
    private static final int INITIAL_DELAY = 0;
    private static final int LIGHTHOUSE_KEEPER_PERIOD = 10;
    private static final long OUTDATED_PERIOD = 1000L * 10L;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(POOL_SIZE);
    private final LightHouseUtils utils;
    private final Jedis jedis;
    private ScheduledFuture<?> lighthouseKeeperHandler;

    LightHouseKeeper(Jedis jedis, LightHouseUtils utils) {
        this.utils = utils;
        this.jedis = jedis;
    }

    void switchOn(CountDownLatch latch) {
        checkSignsOfLife();
        latch.countDown();
    }

    void switchOff() {
        if (lighthouseKeeperHandler != null) {
            lighthouseKeeperHandler.cancel(MAY_INTERRUPT_IF_RUNNING);
            lighthouseKeeperHandler = null;
        }

    }

    private void checkSignsOfLife() {
        final Runnable lighthouseKeeper = () -> {
            try {
                System.out.println("--------------- checkSignsOfLife");
                Long redisTime = utils.getRedisTime(jedis);

                Map<String, Date> lastSignsOfLive = utils.getLastSignsOfLive(jedis);

                Date redisDate = new Date(redisTime);
                lastSignsOfLive.forEach((serverName, lastSignOfLive) -> System.out.println("#############  server " + serverName + " lastSignOfLive " + lastSignOfLive + " redis date " + redisDate));

                lastSignsOfLive.forEach((serverName, lastSignOfLive) -> checkServerTimestamp(serverName, lastSignOfLive, redisTime));

            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        lighthouseKeeperHandler = scheduler.scheduleAtFixedRate(lighthouseKeeper, INITIAL_DELAY, LIGHTHOUSE_KEEPER_PERIOD, SECONDS);

    }

    private void checkServerTimestamp(String serverName, Date lastSignOfLive, Long redisTime) {
        if (new Date(redisTime - OUTDATED_PERIOD).after(lastSignOfLive)) {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!! Server's " + serverName + " signOfLive is outdated : " + lastSignOfLive + " when the current time is " + new Date(redisTime));
        } else {
            System.out.println("Server's " + serverName + " signOfLive is not outdated : " + lastSignOfLive + " when the current time is " + new Date(redisTime));
        }
    }
}
