import { SQSLongPolling } from './sqs-long-polling-consumer';
import { SQS, SNS } from 'aws-sdk';
import { createClient } from 'redis';
import { promisify } from 'util';

async function initialize() {
  const QueueName = 'file-stream-images';
  const redisKey = 'topicArn';

  let sqs = new SQS({ endpoint: 'http://localhost:5001' });
  const { QueueUrl } = await sqs.createQueue({ QueueName }).promise()

  console.info('Queue URL: ', QueueUrl);

  // Get Queue ARN
  const { Attributes } = await sqs.getQueueAttributes({QueueUrl, AttributeNames: ["QueueArn"]}).promise()

  // Get Topic ARN -- ideally this is acheived using AWS Console
  const redisPort = process.env.REDIS_PORT ? +process.env.REDIS_PORT : 6379;
  const redisClient = createClient(redisPort, process.env.REDIS_HOST || 'localhost');
  const getAsync = promisify(redisClient.get).bind(redisClient);

  // Simple retry logic to wait until the topic ARN is retrieved
  let isSuccess = false;
  while (!isSuccess) {
    const topicArn = await getAsync(redisKey);
    console.log('Topic from redis: ', topicArn);
    if (topicArn) {
      isSuccess = true;
      await new SNS({endpoint: 'http://localhost:5002' }).subscribe({
            Protocol: 'sqs',
            TopicArn: topicArn,
            Endpoint: Attributes.QueueArn
          }).promise();
    }
  }
}

initialize().then(
  () => {
    // Simple configuration:
    //  - 2 concurrency listeners
    //  - each listener can receive up to 4 messages
    // With this configuration you could receive and parse 8 `message` events in parallel
    const queue = new SQSLongPolling({
      name: 'file-stream-images',
      maxNumberOfMessages: 4,
      concurrency: 2
    });

    queue.on('message', e => {
      console.log('New message from file-stream-images: ', e.data);
      e.deleteMessage().then(() => {
        e.next();
      });
    });

    queue.on('error', err => {
      console.log('There was an error: ', err);
    });
  }
);