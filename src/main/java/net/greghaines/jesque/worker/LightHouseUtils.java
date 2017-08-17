package net.greghaines.jesque.worker;

import net.greghaines.jesque.utils.JesqueUtils;
import net.greghaines.jesque.utils.ResqueConstants;
import redis.clients.jedis.Jedis;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by dimav on 17/08/17.
 *
 */
class LightHouseUtils {
    private static final String IS_ALIVE = "isAlive";

    private final String namespace;

    LightHouseUtils(String namespace) {
        this.namespace = namespace;
    }

    long getRedisTime(Jedis jedis) {
        List<String> redisTimes = jedis.time();
        long timeInSec = Long.valueOf(redisTimes.get(0));
        long timeInMillis = Long.valueOf(redisTimes.get(1));

        return timeInSec * 1000 + timeInMillis;
    }

    private Date getDate(String lastSignOfLife) {
        return (lastSignOfLife == null) ? null : new Date(Long.valueOf(lastSignOfLife));
    }

    Map<String, Date> getLastSignsOfLive(Jedis jedis) {
        System.out.println("%%%%%%%%%%% Looking for isAlive signs from servers");
        HashMap<String, Date> lastSignsOfLive = new HashMap<>();


        String isAlivePrefix = JesqueUtils.createKey(this.namespace, IS_ALIVE) + ResqueConstants.COLON;
        Set<String> isAliveKeys = jedis.keys(isAlivePrefix + "*");

        isAliveKeys.forEach((isAliveKey) -> {
            String serverName = isAliveKey.substring(isAlivePrefix.length());
            lastSignsOfLive.put(serverName, getDate(jedis.get(isAliveKey)));

        });

        return lastSignsOfLive;
    }

    String getSignOfLifeKey() {
        String serverName = getServerName();
        return JesqueUtils.createKey(this.namespace, IS_ALIVE, serverName);
    }

    private String getServerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException uhe) {
            throw new RuntimeException(uhe);
        }
    }

    Date getLastSignOfLive(Jedis jedis) {
        return getDate(jedis.get(getSignOfLifeKey()));
    }
}
