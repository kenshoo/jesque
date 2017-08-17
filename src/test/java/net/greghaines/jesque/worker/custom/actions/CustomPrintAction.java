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
package net.greghaines.jesque.worker.custom.actions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Complicated constructor to reqs JSON seriCustomTestActionalization.
 *
 * @author Greg Haines
 */
public class CustomPrintAction implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CustomPrintAction.class);
    private static AtomicInteger counter = new AtomicInteger(0);
    private final Integer i;
    private final Double d;
    private final String s;
    private final boolean high;

    public CustomPrintAction(final Integer i, final Double d, final boolean high, final String s) {
        this.i = i;
        this.d = d;
        this.s = s;
        this.high = high;
    }

    public void run() {
        log.info(high?"High":"Low" + " ************ CustomPrintAction.run() {} " + i);


        int counterValue = counter.incrementAndGet();

        if (counterValue == i) {
            log.info(" Counteris " + i);
        }

        if ((counterValue % 5000) == 0) {
            log.info(counterValue + " ************ CustomTestWithLongSleepAction.run() {} {} {}", new Object[]{this.i, this.d , this.s});
        }
    }
}
