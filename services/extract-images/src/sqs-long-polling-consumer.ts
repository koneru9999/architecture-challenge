import { SQS, S3 } from 'aws-sdk';
import { EventEmitter } from 'events';
import { OutgoingMessage, Config } from './models';
import { RetryStartegy, RetryStrategyFactory, RetryStrategies } from './retry-strategy';

export class SQSLongPolling extends EventEmitter {
    private client: SQS | null;
    private url: string | null;
    private config: Config;
    private retryStrategy: RetryStartegy;

    constructor(config: Config) {
      super();
      this.client = null;
      this.url = null;
      this.retryStrategy = RetryStrategyFactory.getRetryStrategy(RetryStrategies.FIBONACCI);
      this.config = {
        region: process.env.AWS_REGION,
        accessKeyId: process.env.AWS_ACCESS_KEY_ID,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
        visibilityTimeout: undefined,
        waitTimeSeconds: 20,
        maxNumberOfMessages: 1,
        concurrency: 1,
        debug: false,
        ...config
      };
  
      this.on('newListener', (name: string) => {
        if (name !== 'message') {
          return;
        }

        if (this.client === null || this.listeners('message').length === 1) {
          return this.connect()
            .then(() => {
                return Array(this.config.concurrency || 1)
                    .fill(null)
                    .forEach((_, index) => this.readQueue(index));
            })
            .catch(err => {
              this.emit('error', err);
            });
        }
      });
  
      if (this.config.debug) {
        this.on('error', err => console.error(err));
        this.on('connect', queue => console.debug('Connected', queue));
      }
    }
  
    connect(): Promise<SQS> {
      return new Promise((resolve, reject) => {
        if (!this.client || !this.url) {
          this.once('connect', () => resolve(this.client!));
          if (this.client && !this.url) {
            return;
          }
        }
        if (this.client) {
          return resolve(this.client);
        }
        this.client = new SQS({
          endpoint: 'http://localhost:5001',
          region: this.config.region,
          accessKeyId: this.config.accessKeyId,
          secretAccessKey: this.config.secretAccessKey,
          retryDelayOptions: {
            customBackoff: (retryCount: number) => {
              return this.retryStrategy.getDelay(retryCount); 
            }
          }
        });
        this.client
          .getQueueUrl({
            QueueName: this.config.name
          })
          .promise()
          .then(data => {
            this.emit('connect', data.QueueUrl!);
            this.url = data.QueueUrl!;
          })
          .catch(err => reject(err));
      });
    }
  
    readQueue(index: number) {
      // Call myself on next tick helper
      const next = () => {
        process.nextTick(() => this.readQueue(index));
      };
  
      // No listeners or hasn't been connected yet.
      if (this.listeners('message').length === 0 || !this.url) {
        return;
      }
  
      if (this.client === null) {
        return;
      }
  
      this.client
        .receiveMessage({
          QueueUrl: this.url,
          AttributeNames: ['All'],
          MaxNumberOfMessages: this.config.maxNumberOfMessages,
          WaitTimeSeconds: this.config.waitTimeSeconds,
          VisibilityTimeout: this.config.visibilityTimeout || 600
        })
        .promise()
        .then(data => {
          if (this.config.debug) {
            console.info('Received message from remote queue', data);
          }
          if (!Array.isArray(data.Messages) || data.Messages.length === 0) {
            return next();
          }
          // Emit each message
          data.Messages.forEach(message => {
            
            this.emit('message', {
              type: 'Message',
              data: JSON.parse(message.Body!) || message.Body,
              message,
              url: this.url,
              name: this.config.name,
              workerIndex: index,
              next,
              deleteMessage: () => this.deleteMessage(message.ReceiptHandle!),
              delay: (timeout: number) =>
                this.changeMessageVisibility(message.ReceiptHandle!, timeout),
              changeMessageVisibility: (timeout: number) =>
                this.changeMessageVisibility(message.ReceiptHandle!, timeout)
            });
          });
        })
        .catch(err => {
          this.emit('error', err);
        });
    }
  
    sendMessage(message: OutgoingMessage) {
      if (!message) {
        message = {
          body: {},
          delay: 0
        };
      }

      return this.connect().then(client => {
        return client
          .sendMessage({
            MessageBody: JSON.stringify(message.body),
            QueueUrl: this.url!,
            DelaySeconds: typeof message.delay === 'number' ? message.delay : 0
          })
          .promise();
      });

    }
  
    deleteMessage(receiptHandle: string) {
      return this.connect().then(client => {
        return client
          .deleteMessage({
            QueueUrl: this.url!,
            ReceiptHandle: receiptHandle
          })
          .promise();
      });
    }
  
    changeMessageVisibility(receiptHandle: string, timeout = 30) {
        return this.connect().then(client => {
        return client
            .changeMessageVisibility({
            QueueUrl: this.url!,
            ReceiptHandle: receiptHandle,
            VisibilityTimeout: timeout
            })
            .promise();
        });
    }
}