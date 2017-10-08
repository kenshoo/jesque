package net.greghaines.jesque;

/**
 * Created by dimav
 * on 14/09/17 22:23.
 */
public class RetryJobException extends RuntimeException{
    private final long delay;
    private final String queueName;

    public RetryJobException(final String message, long delay, String queueName) {
        super(message);
        this.delay = delay;
        this.queueName = queueName;
    }

    public long getDelay() {
        return delay;
    }

    public String getQueueName() {
        return queueName;
    }
}
