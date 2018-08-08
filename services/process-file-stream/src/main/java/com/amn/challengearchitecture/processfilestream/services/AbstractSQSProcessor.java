package com.amn.challengearchitecture.processfilestream.services;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteMessageResult;
import com.amazonaws.services.sqs.model.Message;
import com.amn.challengearchitecture.processfilestream.events.SQSMessageReceivedEvent;
import com.amn.challengearchitecture.processfilestream.properties.AWSProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.util.Assert;

/**
 * Core SQS Message listener to process the messages independently.
 *
 * @author Venkaiah Chowdary Koneru
 */
@Slf4j
public abstract class AbstractSQSProcessor implements ApplicationListener<SQSMessageReceivedEvent> {
    @Autowired
    protected AmazonS3 s3Client;

    @Autowired
    protected AmazonSQS sqsClient;

    @Autowired
    protected AWSProperties awsProperties;

    @Getter
    @Setter
    protected String queueUrl;

    @Getter
    @Setter
    protected String queueArn;

    @Getter
    @Setter
    protected String topicArn;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void onApplicationEvent(SQSMessageReceivedEvent sqsMessageReceivedEvent) {
        prepareProcessing(sqsMessageReceivedEvent.getMessage());
    }

    /**
     * Once the message is received from SQS, <br>
     * <ol>
     * <li>Download file from S3</li>
     * <li>Process the downloaded file</li>
     * <ol>
     * <li>Parse downloaded file</li>
     * <li>Extract content and save to a temp file</li>
     * <li>Upload extracted content to S3</li>
     * <li>Remove temp file</li>
     * </ol>
     * <li>Delete message from queue</li>
     * </ol>
     *
     * @param message
     */
    protected void prepareProcessing(Message message) {
        Assert.notNull(applicationEventPublisher, "publisher is null");
        Assert.notNull(s3Client, "s3 is null");
        Assert.notNull(sqsClient, "sqs is null");

        if (log.isDebugEnabled()) {
            log.debug(message.getBody());
        }

        JSONObject sqsMessage = new JSONObject(message.getBody());
        JSONObject snsMessage = new JSONObject(sqsMessage.getString("Message"));

        if (log.isDebugEnabled()) {
            log.debug("{}", snsMessage.toString());
        }

        final String fileKey = snsMessage.getString("Key");
        final String bucketName = snsMessage.getString("Bucket");

        try {
            // Download File from S3
            S3Object getObjectResponse = s3Client.getObject(new GetObjectRequest(bucketName, fileKey));

            this.process(getObjectResponse.getObjectContent(), fileKey, bucketName);

            // Delete message from SQS
            DeleteMessageRequest deleteMessageRequest = new DeleteMessageRequest(queueUrl, message.getReceiptHandle());

            DeleteMessageResult deleteMessageResponse = sqsClient.deleteMessage(deleteMessageRequest);
            Assert.isTrue(deleteMessageResponse.getSdkHttpMetadata().getHttpStatusCode() == 200,
                    "Error removing the message from SQS");
            log.info("Deleted message of {} from SQS", fileKey);
        } catch (AmazonServiceException e) {
            // The call was transmitted successfully, but corresponding Amazon service couldn't process
            // it, so it returned an error response.
            log.error("Amazon couldn't process the request", e);
        } catch (SdkClientException e) {
            // Corresponding Amazon service couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon service.
            log.error("Amazon client exception", e);
        }
    }

    /**
     * @param s3FileInputStream
     * @param fileKey
     * @param bucketName
     */
    protected abstract void process(S3ObjectInputStream s3FileInputStream,
                                    String fileKey, String bucketName);
}
