import { SQSLongPolling } from './sqs-long-polling-consumer';
import { SQS, SNS } from 'aws-sdk';
import { createClient } from 'redis';

async function initialize() {
  const QueueName = 'file-stream-text';
  let sqs = new SQS({ endpoint: 'http://localhost:5001' });
  const { QueueUrl } = await sqs.createQueue({ QueueName }).promise()

  // Get Queue ARN
  const { Attributes } = await sqs.getQueueAttributes({QueueUrl, AttributeNames: ["QueueArn"]}).promise()

  // Get Topic ARN -- ideally this is acheived using AWS Console
  const redisClient = createClient(+process.env.REDIS_PORT || 6379, process.env.REDIS_HOST || 'localhost');
  redisClient.get('topicArn', async (err, topicArn) => {
    
    console.info("From REDIS for extract-text", topicArn);

    await new SNS({endpoint: 'http://localhost:5002' }).subscribe({
      Protocol: 'sqs',
      TopicArn: topicArn,
      Endpoint: Attributes.QueueArn
    }).promise()
  });
}

initialize().then(
  () => {
    // Simple configuration:
    //  - 2 concurrency listeners
    //  - each listener can receive up to 4 messages
    // With this configuration you could receive and parse 8 `message` events in parallel
    const queue = new SQSLongPolling({
      name: 'file-stream-text',
      maxNumberOfMessages: 4,
      concurrency: 2
    });

    queue.on('message', e => {
      console.log('New message from file-stream-text: ', e.data);
      e.deleteMessage().then(() => {
        e.next();
      });
    });

    queue.on('error', err => {
      console.log('There was an error: ', err);
    });
  }
);