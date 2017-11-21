package net.greghaines.jesque;

/**
 * Created by dimav
 * on 14/09/17 22:23.
 */
public class JobExecutionTimeExpiredException extends IllegalArgumentException{

    public JobExecutionTimeExpiredException(final String message) {
        super(message);
    }
}
