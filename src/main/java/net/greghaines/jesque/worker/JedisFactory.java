package net.greghaines.jesque.worker;

import net.greghaines.jesque.Config;
import redis.clients.jedis.Jedis;

/**
 * Created by dimav on 17/08/17.
 *
 */
public class JedisFactory {

    private Config config;

    public JedisFactory(Config config) {
        this.config = config;
    }

    public Jedis createJedis() {
        return new Jedis(config.getHost(), config.getPort(), config.getTimeout());
    }

}
