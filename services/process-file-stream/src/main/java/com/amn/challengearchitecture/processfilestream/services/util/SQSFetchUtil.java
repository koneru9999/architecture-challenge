package com.amn.challengearchitecture.processfilestream.services.util;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SQSFetchUtil {

    private SQSFetchUtil() {
    }

    /**
     * @param sqsClient
     * @param receiveMessageRequest
     * @param f
     */
    public static void receiveMessages(SqsClient sqsClient,
                                       ReceiveMessageRequest receiveMessageRequest,
                                       int delayTimeInSeconds,
                                       Consumer<Message> f) {
        List<Message> messages = null;
        while (messages == null || messages.size() <= 0) {

            try {
                TimeUnit.SECONDS.sleep(delayTimeInSeconds);
            } catch (InterruptedException e) {
                e.printStackTrace();
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
    public static List<Message> receiveMessages(SqsClient sqsClient,
                                                ReceiveMessageRequest receiveMessageRequest) {
        return sqsClient.receiveMessage(receiveMessageRequest).messages();
    }
}
