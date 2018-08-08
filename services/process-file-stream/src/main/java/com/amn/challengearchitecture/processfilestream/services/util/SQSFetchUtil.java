package com.amn.challengearchitecture.processfilestream.services.util;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Venkaiah Chowdary Koneru
 */
@Slf4j
public final class SQSFetchUtil {

    private SQSFetchUtil() {
    }

    /**
     * @param sqsClient
     * @param receiveMessageRequest
     * @param f
     */
    public static void receiveMessages(AmazonSQS sqsClient,
                                       ReceiveMessageRequest receiveMessageRequest,
                                       int delayTimeInSeconds,
                                       Consumer<Message> f) {
        List<Message> messages = null;
        while (messages == null || messages.size() <= 0) {
            if(log.isDebugEnabled()) {
                log.debug("Waiting before polling on SQS");
            }

            try {
                TimeUnit.SECONDS.sleep(delayTimeInSeconds);
            } catch (InterruptedException e) {
                log.error("Interrupted", e);
            }

            messages = receiveMessages(sqsClient, receiveMessageRequest);
        }

        messages.forEach(f::accept);
    }

    /**
     * @param sqsClient
     * @param receiveMessageRequest
     * @return
     */
    public static List<Message> receiveMessages(AmazonSQS sqsClient,
                                                ReceiveMessageRequest receiveMessageRequest) {
        return sqsClient.receiveMessage(receiveMessageRequest).getMessages();
    }
}
