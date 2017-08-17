package net.greghaines.jesque.worker;

import net.greghaines.jesque.Config;
import redis.clients.jedis.Jedis;

import java.util.Date;
import java.util.concurrent.CountDownLatch;

/**
 * Created by dimav on 17/08/17.
 *
 */
public class Watchdog {
    protected final Config config;
    protected final Jedis jedis;
    private final LightHouse lightHouse;
    private final LightHouseKeeper lightHouseKeeper;
    private final LightHouseUtils utils;


    public Watchdog(Config config, JedisFactory jedisFactory) {
        // do not reuse same jedis in LightHouse and LightHouseKeeper
        // Jedis is not thread safe
        this.config = config;
        this.jedis = jedisFactory.createJedis();
        this.utils = new LightHouseUtils(config.getNamespace());
        this.lightHouse = new LightHouse(jedisFactory.createJedis(), utils);
        this.lightHouseKeeper = new LightHouseKeeper(jedisFactory.createJedis(), utils);
    }

    public void switchOn() {
        CountDownLatch latch = new CountDownLatch(1);
        lightHouseKeeper.switchOn(latch);
        lightHouse.switchOn(latch);
    }

    public void switchOff() {
        Date lastSignOfLife = utils.getLastSignOfLive(jedis);

        System.out.println("last signOfLifeKey is " + lastSignOfLife);

        lightHouse.switchOff();
        lightHouseKeeper.switchOff();
    }
}

