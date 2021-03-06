/**
 The MIT License

 Copyright 2018-2021 Axis Communications AB.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

package com.axis.jenkins.plugins.eiffel.eiffelbroadcaster;

import com.axis.jenkins.plugins.eiffel.eiffelbroadcaster.eiffel.EiffelActivityFinishedEvent;
import com.axis.jenkins.plugins.eiffel.eiffelbroadcaster.eiffel.EiffelEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP;
import hudson.model.AbstractItem;
import hudson.model.Queue;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constants and helper functions.
 * @author Isac Holm &lt;isac.holm@axis.com&gt;
 */
public final class Util {
    private static final Logger logger = LoggerFactory.getLogger(Util.class);
    private static final int NON_PERSISTENT_DELIVERY = 1;
    private static final int PERSISTENT_DELIVERY = 2;

    /** Translate jenkins exit status to eiffel status */
    private static final HashMap<String, EiffelActivityFinishedEvent.Data.Outcome.Conclusion> STATUS_TRANSLATION = new HashMap<>();
    static {
        STATUS_TRANSLATION.put("SUCCESS", EiffelActivityFinishedEvent.Data.Outcome.Conclusion.SUCCESSFUL);
        STATUS_TRANSLATION.put("UNSTABLE", EiffelActivityFinishedEvent.Data.Outcome.Conclusion.UNSUCCESSFUL);
        STATUS_TRANSLATION.put("FAILURE", EiffelActivityFinishedEvent.Data.Outcome.Conclusion.FAILED);
        STATUS_TRANSLATION.put("ABORTED", EiffelActivityFinishedEvent.Data.Outcome.Conclusion.ABORTED);
        STATUS_TRANSLATION.put("x", EiffelActivityFinishedEvent.Data.Outcome.Conclusion.TIMED_OUT);
        STATUS_TRANSLATION.put("INCONCLUSIVE", EiffelActivityFinishedEvent.Data.Outcome.Conclusion.INCONCLUSIVE);
    }

    /**
     * Utility classes should not have a public or default constructor.
     */
    private Util() {
    }

    /**
     * Fetches the full name task name if available.
     *
     * @param t
     * Queue.task
     * @return full name if available, else the short name
     */
    public static String getFullName(Queue.Task t) {
        if (t instanceof AbstractItem) {
            return ((AbstractItem)t).getFullName();
        } else {
            return t.getName();
        }
    }

    public static void publishEvent(EiffelEvent event) {
        EiffelBroadcasterConfig config = EiffelBroadcasterConfig.getInstance();
        if (config != null && config.isBroadcasterEnabled()) {
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .appId(config.getAppId())
                    .deliveryMode(config.getPersistentDelivery() ? PERSISTENT_DELIVERY : NON_PERSISTENT_DELIVERY)
                    .contentType("application/json")
                    .timestamp(Calendar.getInstance().getTime())
                    .build();
            try {
                MQConnection.getInstance().addMessageToQueue(config.getExchangeName(), config.getRoutingKey(),
                        props, event.toJSON().getBytes(StandardCharsets.UTF_8));
            } catch (JsonProcessingException e) {
                logger.error("Unable to serialize object to JSON: {}: {}", e.getMessage(), event);
            }
        }
    }

    /**
     * Translate Jenkins job status into corresponding eiffel status.
     *
     * @param status
     * jenkins job status to translate.
     * @return translated status.
     */
    public static EiffelActivityFinishedEvent.Data.Outcome.Conclusion translateStatus(String status) {
        EiffelActivityFinishedEvent.Data.Outcome.Conclusion statusTranslated;
        if (STATUS_TRANSLATION.get(status) == null) {
            statusTranslated = STATUS_TRANSLATION.get("inconclusive");
        } else {
            statusTranslated = STATUS_TRANSLATION.get(status);
        }
        return statusTranslated;
    }
}
