package com.amn.challengearchitecture.processfilestream.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AWS config properties holder
 *
 * @author Venkaiah Chowdary Koneru
 */
@ConfigurationProperties(prefix = "aws")
@Component
@Data
public class AWSProperties {
    private String region;

    private SQSProperties sqs = new SQSProperties();
    private S3Properties s3 = new S3Properties();
    private SNSProperties sns = new SNSProperties();

    /**
     * AWS S3 properties holder
     */
    @Data
    public static class S3Properties {
        private String endPoint;
        private String bucketName;
    }

    /**
     * AWS SNS properties holder
     */
    @Data
    public static class SNSProperties {
        private String endPoint;
    }

    /**
     * AWS SQS properties holder
     */
    @Data
    public static class SQSProperties {
        private String endPoint;
        private String queueName;
    }


}
